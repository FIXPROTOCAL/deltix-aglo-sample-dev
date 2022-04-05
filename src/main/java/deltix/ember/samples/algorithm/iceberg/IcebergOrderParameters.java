package deltix.ember.samples.algorithm.iceberg;


import deltix.ember.message.trade.CustomAttribute;
import deltix.ember.message.trade.OrderEntryRequest;
import deltix.ember.service.algorithm.slicer.AlgoOrderParameters;
import deltix.gflog.Log;
import deltix.gflog.LogFactory;

class IcebergOrderParameters extends AlgoOrderParameters {
    private static final Log LOG = LogFactory.getLog(IcebergOrderParameters.class);
    @Override
    protected void parseAttribute(CustomAttribute attribute, OrderEntryRequest request) {
        LOG.info("parseAttribute:"+request);
        switch (attribute.getKey()) {
            default:
                super.parseAttribute(attribute, request);
        }
    }

    @Override
    public void copyFrom(AlgoOrderParameters other) {
        super.copyFrom(other);
    }

    @Override
    public void reset() {
        super.reset();
    }
}
