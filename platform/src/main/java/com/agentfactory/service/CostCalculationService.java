package com.agentfactory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class CostCalculationService {

    private final Map<String, BigDecimal> pricePerToken;

    public CostCalculationService(
            @Value("#{${af.pricing:{'gpt-4o':'0.0000025','gpt-4o-mini':'0.00000015','claude-sonnet-4':'0.000003','claude-opus-4':'0.000015','deepseek-v3':'0.00000027'}}}") Map<String, String> pricing) {
        this.pricePerToken = new java.util.HashMap<>();
        pricing.forEach((model, price) -> this.pricePerToken.put(model, new BigDecimal(price)));
    }

    public BigDecimal calculateCost(int tokensIn, int tokensOut, String modelId) {
        BigDecimal price = pricePerToken.get(modelId);
        if (price == null) return BigDecimal.ZERO;
        return price.multiply(BigDecimal.valueOf(tokensIn + tokensOut)).setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal estimateCost(String inputText, String modelId) {
        BigDecimal price = pricePerToken.get(modelId);
        if (price == null) return BigDecimal.valueOf(-1);
        int estimatedInputTokens = inputText.length() / 4;
        int estimatedTotalTokens = estimatedInputTokens * 2 * 5;
        return price.multiply(BigDecimal.valueOf(estimatedTotalTokens)).setScale(6, RoundingMode.HALF_UP);
    }

    public boolean isModelKnown(String modelId) {
        return pricePerToken.containsKey(modelId);
    }
}
