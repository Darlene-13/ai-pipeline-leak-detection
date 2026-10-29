package io.github.darlene.leakdetectionapplication.alert;

import io.github.darlene.leakdetectionapplication.analytics.*;
import io.github.darlene.leakdetectionapplication.auth.*;
import io.github.darlene.leakdetectionapplication.configuration.*;
import io.github.darlene.leakdetectionapplication.messaging.*;
import io.github.darlene.leakdetectionapplication.monitoring.*;
import io.github.darlene.leakdetectionapplication.pipeline.*;
import io.github.darlene.leakdetectionapplication.recommendation.*;
import io.github.darlene.leakdetectionapplication.sensor.*;
import io.github.darlene.leakdetectionapplication.simulation.*;
import io.github.darlene.leakdetectionapplication.shared.*;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.github.darlene.leakdetectionapplication.sensor.SensorReading;
import io.github.darlene.leakdetectionapplication.analytics.AnalyticsSummaryResponse;
import io.github.darlene.leakdetectionapplication.pipeline.MLPredictionResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final FaultAlertRepository faultAlertRepository;
    private final FaultAlertMapper faultAlertMapper;
    private final SensorReadingService sensorReadingService;

    public FaultAlertResponse saveAlert(SensorReading reading,
                                        MLPredictionResponse prediction,
                                        String recommendation,
                                        long latencyMs) {
        //For you to save an alert you have to build the alert
        FaultAlert alert = FaultAlert.builder()
                .sensorReading(reading)
                .deviceId(reading.getDeviceId())
                .readingTime(reading.getReadingTime())
                .faultClass(FaultClass.valueOf(prediction.getPredictedClass()))
                .severityLevel(deriveSeverity(
                        prediction.getConfidence(),
                        prediction.getPredictedClass()))
                .confidence(prediction.getConfidence())
                .recommendation(recommendation)
                .latencyMs(latencyMs)
                .build();

        FaultAlert savedAlert = faultAlertRepository.save(alert);

        log.info("Alert saved for reading ID: {} → class: {} confidence: {}%",
                reading.getId(),
                prediction.getPredictedClass(),
                prediction.getConfidence() * 100);

        return faultAlertMapper.toResponse(savedAlert);
    }

    public Page<FaultAlertResponse> getRecentAlerts(int page, int size) {
        return faultAlertRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(faultAlertMapper::toResponse);
    }

    public FaultAlertResponse getAlertById(Long id) {
        FaultAlert alert = faultAlertRepository.findById(id)
                .orElseThrow(() -> new FaultAlertNotFoundException(
                        "Fault alert with ID " + id + " not found"));

        return faultAlertMapper.toResponse(alert);
    }

    public List<FaultAlertResponse> getAlertsByDateRange(
            OffsetDateTime from, OffsetDateTime to) {

        List<FaultAlert> alerts = faultAlertRepository
                .findByCreatedAtBetween(from, to);

        return faultAlertMapper.toResponseList(alerts);
    }

    public List<FaultAlertResponse> getAlertsByFaultClass(FaultClass faultClass) {
        List<FaultAlert> alerts = faultAlertRepository
                .findByFaultClass(faultClass);

        return faultAlertMapper.toResponseList(alerts);
    }

    public Optional<FaultAlertResponse> getMostRecentAlert() {
        return faultAlertRepository
                .findTopByOrderByCreatedAtDesc()
                .map(faultAlertMapper::toResponse);
    }

    public AnalyticsSummaryResponse getAnalyticsSummary(
            OffsetDateTime from, OffsetDateTime to) {

        List<SensorReading> readings = sensorReadingService
                .getReadingEntitiesByDateRange(from, to);

        // Fault counts
        long leakCount = faultAlertRepository.countByFaultClass(FaultClass.LEAK);
        long blockageCount = faultAlertRepository.countByFaultClass(FaultClass.BLOCKAGE);
        long totalReadings = readings.size();
        long normalCount = Math.max(0, totalReadings - leakCount - blockageCount);

        // Average pressure across all three nodes per reading
        double averagePressure = readings.stream()
                .mapToDouble(r -> (r.getNodeAPressure()
                        + r.getNodeBPressure()
                        + r.getNodeCPressure()) / 3.0)
                .average()
                .orElse(0.0);

        // Min pressure across all three nodes
        double minPressure = readings.stream()
                .mapToDouble(r -> Math.min(r.getNodeAPressure(),
                        Math.min(r.getNodeBPressure(), r.getNodeCPressure())))
                .min()
                .orElse(0.0);

        // Max pressure across all three nodes
        double maxPressure = readings.stream()
                .mapToDouble(r -> Math.max(r.getNodeAPressure(),
                        Math.max(r.getNodeBPressure(), r.getNodeCPressure())))
                .max()
                .orElse(0.0);

        // Average flow velocity
        double averageFlowVelocity = readings.stream()
                .mapToDouble(r -> (r.getVelocityA() + r.getVelocityB() + r.getVelocityC()) / 3.0)
                .average()
                .orElse(0.0);

        log.debug("Analytics summary built for range: {} to {}", from, to);

        return AnalyticsSummaryResponse.builder()
                .normalCount((int) normalCount)
                .leakCount((int) leakCount)
                .blockageCount((int) blockageCount)
                .totalReadings((int) totalReadings)
                .averagePressure(averagePressure)
                .minPressure(minPressure)
                .maxPressure(maxPressure)
                .averageVelocity(averageFlowVelocity)
                .fromDate(from)
                .toDate(to)
                .build();
    }

    private SeverityLevel deriveSeverity(double confidence, String predictedClass) {
        if ("NORMAL".equalsIgnoreCase(predictedClass)) {
            return SeverityLevel.NONE;
        }
        if (confidence < 0.50) {
            return SeverityLevel.LOW;
        }
        if (confidence < 0.75) {
            return SeverityLevel.MODERATE;
        }
        return SeverityLevel.CRITICAL;
    }
}