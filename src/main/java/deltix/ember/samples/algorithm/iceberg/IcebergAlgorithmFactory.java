package deltix.ember.samples.algorithm.iceberg;

import deltix.anvil.util.annotation.Optional;
import deltix.anvil.util.codec.AlphanumericCodec;
import deltix.ember.service.algorithm.AbstractAlgorithmFactory;
import deltix.ember.service.algorithm.AlgorithmContext;
import deltix.gflog.Log;
import deltix.gflog.LogFactory;


public class IcebergAlgorithmFactory extends AbstractAlgorithmFactory {
    @Optional
    private String defaultOrderDestination = "COINBASE";
    private static final Log LOG = LogFactory.getLog(IcebergAlgorithmFactory.class);

    public String getDefaultOrderDestination() {
        return defaultOrderDestination;
    }

    public void setDefaultOrderDestination(final String defaultOrderDestination) {
        this.defaultOrderDestination = defaultOrderDestination;
    }

    @Override
    public IcebergAlgorithm create(final AlgorithmContext context) {
        LOG.info("create");
        return new IcebergAlgorithm(context, getCacheSettings(), AlphanumericCodec.encode(defaultOrderDestination));
    }
}
