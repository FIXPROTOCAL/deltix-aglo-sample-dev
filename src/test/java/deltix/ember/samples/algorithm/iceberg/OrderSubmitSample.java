package deltix.ember.samples.algorithm.iceberg;

import deltix.anvil.util.codec.AlphanumericCodec;
import com.epam.deltix.dfp.Decimal64Utils;
import deltix.ember.message.trade.*;
import deltix.ember.sample.SampleSupportTools;
import deltix.util.collections.generated.ObjectArrayList;
import deltix.util.collections.generated.ObjectList;

/**
 * Sample that illustrates how to send new trade order and listen for trading events
 */
public class OrderSubmitSample extends SampleSupportTools {
    public static void main(String[] args) throws InterruptedException {
        sendRequest(
                (publication) -> {
                    OrderNewRequest request = createNewOrderRequest(Side.BUY, 120, "BTC/USD", 38500);
                    publication.onNewOrderRequest(request);
                    System.out.println("New order request was sent " + request.getSourceId() + ':' + request.getOrderId());
                }
        );
    }

    private static MutableOrderNewRequest createNewOrderRequest(Side side, int size, String symbol, double price) {
        MutableOrderNewRequest request = new MutableOrderNewRequest();
        request.setOrderId(Long.toString(System.currentTimeMillis() % 100000000000L));
        request.setSide(side);
        request.setQuantity(Decimal64Utils.fromLong((long) size));
        request.setSymbol(symbol);
        request.setLimitPrice(Decimal64Utils.fromDouble(price));
        request.setTimeInForce(request.hasLimitPrice() ? TimeInForce.DAY : TimeInForce.IMMEDIATE_OR_CANCEL);
        request.setDisplayQuantity(Decimal64Utils.fromLong((long) (size / 10)));
        request.setOrderType(request.hasLimitPrice() ? OrderType.LIMIT : OrderType.MARKET);
        request.setDestinationId(AlphanumericCodec.encode("TWAP"));
        request.setExchangeId(AlphanumericCodec.encode("#FILL"));
        request.setSourceId(CLIENT_SOURCE_ID); // Identify order source
        request.setTimestamp(System.currentTimeMillis());
        ObjectArrayList<CustomAttribute> attributes = new ObjectArrayList<CustomAttribute>();
        MutableCustomAttribute attr = new MutableCustomAttribute();
        attr.setKey(6002);
        attr.setValue("01:00:00");
        attributes.add(attr);

        MutableCustomAttribute attr_drip = new MutableCustomAttribute();
        attr_drip.setKey(6023);
        attr_drip.setValue("10");
        attributes.add(attr_drip);


        //request.setAttributes(attributes);

        MutableCustomAttribute attr_drip2 = new MutableCustomAttribute();
        attr_drip2.setKey(6045);
        attr_drip2.setValue("38510");
        attributes.add(attr_drip2);

        request.setAttributes(attributes);

        return request;
    }
}
