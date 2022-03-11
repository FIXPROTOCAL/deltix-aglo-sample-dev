package deltix.ember.samples.algorithm.xhub;

import deltix.anvil.util.Factory;
import deltix.ember.service.algorithm.AbstractAlgorithm;
import deltix.ember.service.algorithm.AlgorithmContext;
import deltix.ember.service.algorithm.ChildOrder;
import deltix.ember.service.algorithm.md.InstrumentData;
import deltix.ember.service.oms.cache.OrdersCacheSettings;

import java.util.function.Function;

public class TWAPAlgorithm extends AbstractAlgorithm<TWAPOrder, InstrumentData> {
    TWAPAlgorithm(AlgorithmContext context, OrdersCacheSettings cacheSettings) {
        super( context,cacheSettings);
    }

    @Override
    protected Factory<TWAPOrder> createParentOrderFactory() {
        return null;
    }

    @Override
    protected Factory<ChildOrder<TWAPOrder>> createChildOrderFactory() {
        return null;
    }

    @Override
    protected Function<CharSequence, InstrumentData> createInstrumentInfoFactory() {
        return null;
    }
}
