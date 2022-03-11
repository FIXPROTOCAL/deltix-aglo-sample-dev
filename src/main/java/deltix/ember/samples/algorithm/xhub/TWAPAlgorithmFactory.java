package deltix.ember.samples.algorithm.xhub;

import deltix.ember.service.algorithm.AbstractAlgorithmFactory;
import deltix.ember.service.algorithm.AlgorithmContext;

public class TWAPAlgorithmFactory extends AbstractAlgorithmFactory {
    @Override
    public TWAPAlgorithm create(AlgorithmContext context) {
        return new TWAPAlgorithm(context, getCacheSettings());
    }
}

