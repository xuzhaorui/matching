package com.match;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Based on Java Vector API The batch vectorization matching pipeline
 */
public class VectorizedMatchPipeline {
    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_256;

    /**
     * Match the score difference between each pair of players i<j in the batch and their respective matchRanges.
     * Once met, mark Matched(i) & markMatched(j) at the same time, and skip to the next i.
     */
    public static void processBatch(MatchEvent[] batch, List<MatchPair> pairs) {
        int n = batch.length;

        // 1) Extract native arrays
        int[] scores = extract(batch, MatchEvent::getScore);
        int[] ranges = extract(batch, MatchEvent::getMatchRange);

        // 2) Sort by score, record the original subscript
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingInt(a -> scores[a]));

        int[] sortedScores = new int[n], sortedRanges = new int[n], sortedIdx = new int[n];
        for (int i = 0; i < n; i++) {
            sortedIdx[i]    = idx[i];
            sortedScores[i] = scores[idx[i]];
            sortedRanges[i] = ranges[idx[i]];
        }

        // Temporarily used to unfold the VectorMask
        boolean[] laneMask = new boolean[SPECIES.length()];

        // 3) For each player i, find the upper bound of score[i]+range[i] and scan candidate j
        for (int a = 0; a < n; a++) {
            int iOrig = sortedIdx[a];
            // Only those who are in PROCESSING have a chance
            if (batch[iOrig].getStateCode() != MatchEvent.PROCESSING) continue;

            int scoreI = sortedScores[a];
            int rangeI = sortedRanges[a];
            // binarySearch Find the first one > scoreI + rangeI
            int upTo = Arrays.binarySearch(sortedScores, a + 1, n, scoreI + rangeI + 1);
            if (upTo < 0) upTo = -upTo - 1;
            if (upTo <= a + 1) continue;

            //Scan [a+1, upTo) intervals, vectorized comparisons |scoreJ - scoreI| ≤ rangeI ∧ |scoreJ - scoreI| ≤ rangeJ
            for (int j = a + 1; j < upTo; j += SPECIES.length()) {
                VectorMask<Integer> inRange  = SPECIES.indexInRange(j, upTo);
                IntVector          vecScoreJ = IntVector.fromArray(SPECIES, sortedScores, j, inRange);
                IntVector          vecRangeJ = IntVector.fromArray(SPECIES, sortedRanges, j, inRange);

                // Calculate scoreJ - scoreI (not negative because sorted)
                IntVector diff = vecScoreJ.sub(scoreI);
                // diff ≤ rangeI
                VectorMask<Integer> m1 = diff.compare(VectorOperators.LE, rangeI);
                // diff ≤ rangeJ
                VectorMask<Integer> m2 = diff.compare(VectorOperators.LE, vecRangeJ);

                VectorMask<Integer> matchMask = inRange.and(m1).and(m2);
                if (!matchMask.anyTrue()) continue;

                // Find the first lane that matches
                matchMask.intoArray(laneMask, 0);
                for (int lane = 0; lane < SPECIES.length() && j + lane < upTo; lane++) {
                    if (!laneMask[lane]) continue;
                    int bOrig = sortedIdx[j + lane];
                    // Both sides are in PROCESSING, and then do CAS
                    if (batch[bOrig].markMatched()) {
                        batch[iOrig].markMatched();
                        MatchPair matchPair = new MatchPair();
                        matchPair.init(batch[bOrig],batch[iOrig]);
                        pairs.add(matchPair);

                    }
                    j = upTo;  // End the outer j-loop
                    break; // i just need to be paired with the first j that matches
                }
            }
        }
    }

    /**
     * 跨桶批量匹配：batchA vs batchB
     */
    public static void processCrossBatch(MatchEvent[] batchA,
                                         MatchEvent[] batchB,
                                         List<MatchPair> pairs) {
        int nA = batchA.length;
        int nB = batchB.length;
        int[] statesA = extract(batchA, MatchEvent::getStateCode);
        int[] scoresA = extract(batchA, MatchEvent::getScore);
        int[] rangesA = extract(batchA, MatchEvent::getMatchRange);
        int[] statesB = extract(batchB, MatchEvent::getStateCode);
        int[] scoresB = extract(batchB, MatchEvent::getScore);
        int[] rangesB = extract(batchB, MatchEvent::getMatchRange);

        Integer[] idxA = new Integer[nA];
        for (int i = 0; i < nA; i++) idxA[i] = i;
        Arrays.sort(idxA, Comparator.comparingInt(a -> scoresA[a]));
        Integer[] idxB = new Integer[nB];
        for (int i = 0; i < nB; i++) idxB[i] = i;
        Arrays.sort(idxB, Comparator.comparingInt(b -> scoresB[b]));

        int[] sortedScoresA = new int[nA], sortedRangesA = new int[nA], sortedIdxA = new int[nA];
        for (int i = 0; i < nA; i++) {
            sortedIdxA[i]    = idxA[i];
            sortedScoresA[i] = scoresA[idxA[i]];
            sortedRangesA[i] = rangesA[idxA[i]];
        }
        int[] sortedScoresB = new int[nB], sortedRangesB = new int[nB], sortedIdxB = new int[nB];
        for (int i = 0; i < nB; i++) {
            sortedIdxB[i]    = idxB[i];
            sortedScoresB[i] = scoresB[idxB[i]];
            sortedRangesB[i] = rangesB[idxB[i]];
        }

        boolean[] laneMask = new boolean[SPECIES.length()];

        for (int a = 0; a < nA; a++) {
            int iOrig = sortedIdxA[a];
            if (batchA[iOrig].getStateCode() != MatchEvent.PROCESSING) continue;

            int scoreI = sortedScoresA[a];
            int rangeI = sortedRangesA[a];
            // B 范围二分：[scoreI-rangeI, scoreI+rangeI]
            int lowerVal = scoreI - rangeI;
            int upperVal = scoreI + rangeI;
            int start = Math.max(0, Arrays.binarySearch(sortedScoresB, 0, nB, lowerVal));
            if (start < 0) start = -start - 1;
            int end = Math.min(nB, Arrays.binarySearch(sortedScoresB, start, nB, upperVal + 1));
            if (end < 0) end = -end - 1;
            if (start >= end) continue;

            for (int j = start; j < end; j += SPECIES.length()) {
                int chunk = Math.min(SPECIES.length(), end - j);
                VectorMask<Integer> inRange = SPECIES.indexInRange(j, j + chunk);
                IntVector vecBScore = IntVector.fromArray(SPECIES, sortedScoresB, j, inRange);
                IntVector vecBRange = IntVector.fromArray(SPECIES, sortedRangesB, j, inRange);
                IntVector diff     = vecBScore.sub(scoreI);

                VectorMask<Integer> mA = diff.compare(VectorOperators.LE, rangeI)
                        .and(diff.compare(VectorOperators.GE, -rangeI));
                IntVector negBRange = vecBRange.neg();
                VectorMask<Integer> mB = diff.compare(VectorOperators.LE, vecBRange)
                        .and(diff.compare(VectorOperators.GE, negBRange));
                VectorMask<Integer> matchMask = inRange.and(mA).and(mB);
                if (!matchMask.anyTrue()) continue;

                matchMask.intoArray(laneMask, 0);
                for (int lane = 0; lane < chunk; lane++) {
                    if (!laneMask[lane]) continue;
                    int bOrig = sortedIdxB[j + lane];
                    // CAS 双向标记
                    if (batchB[bOrig].markMatched()) {
                        if (batchA[iOrig].markMatched()) {
                            MatchPair pair = new MatchPair();
                            pair.init(batchB[bOrig], batchA[iOrig]);
                            pairs.add(pair);
                        }
                    }
                    j = end; break;
                }
            }
        }
    }

    private static int[] extract(MatchEvent[] batch, ToIntFunction<MatchEvent> fn) {
        try {
            int[] arr = new int[batch.length];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = fn.applyAsInt(batch[i]);
            }
            return arr;
        } catch (Exception e) {
            System.out.println("extract error");
            throw new RuntimeException(e);
        }
    }

    private static int[] extract(MatchEvent[] batch,  int count, ToIntFunction<MatchEvent> fn) {
        try {
            int[] arr = new int[count];
            for (int i = 0; i < count; i++) {
                arr[i] = fn.applyAsInt(batch[i]);
            }
            return arr;
        } catch (Exception e) {
            System.out.println("extract error");
            throw new RuntimeException(e);
        }
    }
    public static void processBatch(MatchEvent[] batch, int count, List<MatchPair> pairs) {
        int n = count;

        // 1) 提取原生数组
//        int[] states = extract(batch, MatchEvent::getStateCode);
        int[] scores = extract(batch, count, MatchEvent::getScore);
        int[] ranges = extract(batch, count, MatchEvent::getMatchRange);

        // 2) 按 score 排序，记录原始下标
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingInt(a -> scores[a]));

        int[] sortedScores = new int[n], sortedRanges = new int[n], sortedIdx = new int[n];
        for (int i = 0; i < n; i++) {
            sortedIdx[i]    = idx[i];
            sortedScores[i] = scores[idx[i]];
            sortedRanges[i] = ranges[idx[i]];
        }

        // 临时用于展开 VectorMask
        boolean[] laneMask = new boolean[SPECIES.length()];

        // 3) 对每个玩家 i，找到 score[i]+range[i] 的上界，扫描候选 j
        for (int a = 0; a < n; a++) {
            int iOrig = sortedIdx[a];
            // 只有处于 PROCESSING 的才有机会
            if (batch[iOrig].getStateCode() != MatchEvent.PROCESSING) continue;

            int scoreI = sortedScores[a];
            int rangeI = sortedRanges[a];
            // binarySearch 找第一个 > scoreI + rangeI
            int upTo = Arrays.binarySearch(sortedScores, a + 1, n, scoreI + rangeI + 1);
            if (upTo < 0) upTo = -upTo - 1;
            if (upTo <= a + 1) continue;

            // 扫描 [a+1, upTo) 区间，向量化比较 |scoreJ - scoreI| ≤ rangeI ∧ |scoreJ - scoreI| ≤ rangeJ
            for (int j = a + 1; j < upTo; j += SPECIES.length()) {
                VectorMask<Integer> inRange  = SPECIES.indexInRange(j, upTo);
                IntVector          vecScoreJ = IntVector.fromArray(SPECIES, sortedScores, j, inRange);
                IntVector          vecRangeJ = IntVector.fromArray(SPECIES, sortedRanges, j, inRange);

                // 计算 scoreJ - scoreI（非负，因为已排序）
                IntVector diff = vecScoreJ.sub(scoreI);
                // diff ≤ rangeI
                VectorMask<Integer> m1 = diff.compare(VectorOperators.LE, rangeI);
                // diff ≤ rangeJ
                VectorMask<Integer> m2 = diff.compare(VectorOperators.LE, vecRangeJ);

                VectorMask<Integer> matchMask = inRange.and(m1).and(m2);
                if (!matchMask.anyTrue()) continue;

                // 找到第一个匹配的 lane
                matchMask.intoArray(laneMask, 0);
                for (int lane = 0; lane < SPECIES.length() && j + lane < upTo; lane++) {
                    if (!laneMask[lane]) continue;
                    int bOrig = sortedIdx[j + lane];
                    // 双方都处于 PROCESSING，再做一次 CAS
                    if (batch[bOrig].markMatched()) {
                        batch[iOrig].markMatched();
                        MatchPair matchPair = new MatchPair();
                        matchPair.init(batch[bOrig],batch[iOrig]);
                        pairs.add(matchPair);

                    }
                    j = upTo;  // 结束外层 j-loop
                    break; // i 只需与第一个符合的 j 配对
                }
//                break; // 找到一次就跳到下一个 i
            }
        }
    }


}
