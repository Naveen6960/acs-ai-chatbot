package com.example.azureopenaiapi.pdfqa.embedding;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
public class EmbeddingService {

    private final OpenAIClient openAIClient;
    private final String embeddingDeployment;

    public EmbeddingService(OpenAIClient openAIClient,
                            @Value("${pdfqa.openai.embeddingDeployment}") String embeddingDeployment) {
        this.openAIClient = openAIClient;
        this.embeddingDeployment = embeddingDeployment; // e.g., text-embedding-3-large
    }

    public float[] embed(String text) {
        EmbeddingsOptions opts = new EmbeddingsOptions(List.of(text));
        Embeddings res = openAIClient.getEmbeddings(embeddingDeployment, opts);

        // Works whether the SDK returns List<Float> or List<Double>
        List<? extends Number> v = res.getData().get(0).getEmbedding();

        float[] out = new float[v.size()];
        for (int i = 0; i < v.size(); i++) {
            out[i] = v.get(i).floatValue();
        }
        return out;
    }

}
