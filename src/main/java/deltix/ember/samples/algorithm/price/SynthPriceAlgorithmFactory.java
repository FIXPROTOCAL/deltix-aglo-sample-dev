package deltix.ember.samples.algorithm.price;

import deltix.anvil.util.annotation.Optional;
import deltix.ember.service.algorithm.AbstractAlgorithmFactory;
import deltix.ember.service.algorithm.AlgorithmContext;


@SuppressWarnings("unused")
public class SynthPriceAlgorithmFactory extends AbstractAlgorithmFactory {
    @Optional
    private String outputStreamKey = "syntheticPrices";

    public String getOutputStreamKey() {
        return outputStreamKey;
    }

    public void setOutputStreamKey(final String outputStreamKey) {
        this.outputStreamKey = outputStreamKey;
    }

    @Override
    public SynthPriceAlgorithm create(final AlgorithmContext context) {
        return new SynthPriceAlgorithm(context, getCacheSettings(), outputStreamKey);
    }
}
