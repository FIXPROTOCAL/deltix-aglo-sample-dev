package deltix.ember.samples.algorithm.xhub;

import deltix.anvil.util.CharSequenceParser;
import deltix.anvil.util.TypeConstants;
import deltix.anvil.util.parser.TimestampParser;
import deltix.ember.message.trade.CustomAttribute;
import deltix.ember.message.trade.OrderRequest;
import deltix.ember.service.algorithm.AlgoOrder;
import deltix.util.collections.generated.ObjectList;

class TWAPOrder extends AlgoOrder {
    long startTime = TypeConstants.TIMESTAMP_NULL;
    long endTime = TypeConstants.TIMESTAMP_NULL;
    double dripPercentage;

    static final int START_TIME_ATTRIBUTE_KEY = 6021;
    static final int END_TIME_ATTRIBUTE_KEY = 6022;
    static final int DRIP_PERCENTAGE_ATTRIBUTE_KEY = 6023;


    void copyExtraAttributes(OrderRequest request) {
        if (request.hasAttributes()) {
            ObjectList<CustomAttribute> attributes = request.getAttributes();
            for (int i = 0, size = attributes.size(); i < size; i++) {
                CustomAttribute attribute = attributes.get(i);

                switch (attribute.getKey()) {
                    case START_TIME_ATTRIBUTE_KEY:
                        startTime = TimestampParser.parseTimestamp(attribute.getValue());
                        break;
                    case END_TIME_ATTRIBUTE_KEY:
                        endTime = TimestampParser.parseTimestamp(attribute.getValue());
                        break;
                    case DRIP_PERCENTAGE_ATTRIBUTE_KEY:
                        dripPercentage = CharSequenceParser.parseInt(attribute.getValue());
                        break;
                }
            }
        }
    }
}