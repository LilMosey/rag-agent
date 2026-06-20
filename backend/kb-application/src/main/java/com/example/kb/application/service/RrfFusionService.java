package com.example.kb.application.service;

import com.example.kb.application.port.VectorIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RrfFusionService {

    private static final Logger log = LoggerFactory.getLogger(RrfFusionService.class);

    public List<VectorIndexSearcher.SearchHit> fuse(
            List<RagRetrievalService.RetrievalTaskReport> taskReports,
            int fusionTopK,
            int rrfK
    ) {
        log.info("RRF 融合入参: taskCount={}, fusionTopK={}, rrfK={}", taskReports.size(), fusionTopK, rrfK);
        Map<Long, FusedHitAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (RagRetrievalService.RetrievalTaskReport taskReport : taskReports) {
            if (!taskReport.success()) {
                log.info("RRF 融合分支: 跳过非成功任务, taskType={}, status={}",
                        taskReport.taskType(), taskReport.status());
                continue;
            }
            int rankNo = 1;
            for (VectorIndexSearcher.SearchHit hit : taskReport.hits()) {
                FusedHitAccumulator accumulator = accumulatorMap.computeIfAbsent(
                        hit.chunkId(),
                        chunkId -> new FusedHitAccumulator(hit)
                );
                accumulator.addScore(BigDecimal.ONE.divide(
                        BigDecimal.valueOf(rrfK + rankNo),
                        10,
                        RoundingMode.HALF_UP
                ));
                rankNo++;
            }
        }
        List<FusedHitAccumulator> accumulators = new ArrayList<>(accumulatorMap.values());
        accumulators.sort(Comparator.comparing(FusedHitAccumulator::score).reversed());
        List<VectorIndexSearcher.SearchHit> fusedHits = accumulators.stream()
                .limit(fusionTopK)
                .map(FusedHitAccumulator::toSearchHit)
                .toList();
        log.info("RRF 融合出参: fusedCount={}", fusedHits.size());
        return fusedHits;
    }

    private static class FusedHitAccumulator {
        private final VectorIndexSearcher.SearchHit representativeHit;
        private BigDecimal score = BigDecimal.ZERO;

        private FusedHitAccumulator(VectorIndexSearcher.SearchHit representativeHit) {
            this.representativeHit = representativeHit;
        }

        private void addScore(BigDecimal value) {
            this.score = this.score.add(value);
        }

        private BigDecimal score() {
            return score;
        }

        private VectorIndexSearcher.SearchHit toSearchHit() {
            return new VectorIndexSearcher.SearchHit(
                    representativeHit.knowledgeBaseId(),
                    representativeHit.fileId(),
                    representativeHit.chunkId(),
                    representativeHit.chunkIndex(),
                    score.setScale(6, RoundingMode.HALF_UP)
            );
        }
    }
}
