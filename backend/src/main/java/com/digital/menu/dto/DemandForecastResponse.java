package com.digital.menu.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DemandForecastResponse {
    private Instant generatedAt;
    private int lookbackDays;
    private int horizonDays;
    private int predictedOrders;
    private double predictedRevenue;
    private double trendFactor;
    private String engineUsed;
    private String modelUsed;
    private String notes;
    private List<DemandForecastItem> itemForecasts = new ArrayList<>();

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public int getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(int lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    public int getHorizonDays() {
        return horizonDays;
    }

    public void setHorizonDays(int horizonDays) {
        this.horizonDays = horizonDays;
    }

    public int getPredictedOrders() {
        return predictedOrders;
    }

    public void setPredictedOrders(int predictedOrders) {
        this.predictedOrders = predictedOrders;
    }

    public double getPredictedRevenue() {
        return predictedRevenue;
    }

    public void setPredictedRevenue(double predictedRevenue) {
        this.predictedRevenue = predictedRevenue;
    }

    public double getTrendFactor() {
        return trendFactor;
    }

    public void setTrendFactor(double trendFactor) {
        this.trendFactor = trendFactor;
    }

    public String getEngineUsed() {
        return engineUsed;
    }

    public void setEngineUsed(String engineUsed) {
        this.engineUsed = engineUsed;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<DemandForecastItem> getItemForecasts() {
        return itemForecasts;
    }

    public void setItemForecasts(List<DemandForecastItem> itemForecasts) {
        this.itemForecasts = itemForecasts;
    }
}
