package com.digital.menu.dto;

public class DemandForecastItem {
    private String dishId;
    private String dishName;
    private String category;
    private int soldLastLookback;
    private double avgDailyDemand;
    private int predictedDemand;
    private int recommendedParStock;
    private String recommendation;

    public String getDishId() {
        return dishId;
    }

    public void setDishId(String dishId) {
        this.dishId = dishId;
    }

    public String getDishName() {
        return dishName;
    }

    public void setDishName(String dishName) {
        this.dishName = dishName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getSoldLastLookback() {
        return soldLastLookback;
    }

    public void setSoldLastLookback(int soldLastLookback) {
        this.soldLastLookback = soldLastLookback;
    }

    public double getAvgDailyDemand() {
        return avgDailyDemand;
    }

    public void setAvgDailyDemand(double avgDailyDemand) {
        this.avgDailyDemand = avgDailyDemand;
    }

    public int getPredictedDemand() {
        return predictedDemand;
    }

    public void setPredictedDemand(int predictedDemand) {
        this.predictedDemand = predictedDemand;
    }

    public int getRecommendedParStock() {
        return recommendedParStock;
    }

    public void setRecommendedParStock(int recommendedParStock) {
        this.recommendedParStock = recommendedParStock;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }
}
