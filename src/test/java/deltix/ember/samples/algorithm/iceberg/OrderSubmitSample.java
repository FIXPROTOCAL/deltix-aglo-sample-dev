package deltix.ember.samples.algorithm.iceberg;

import deltix.anvil.util.codec.AlphanumericCodec;
import deltix.dfp.Decimal64Utils;
import deltix.ember.message.trade.*;
import deltix.ember.sample.SampleSupportTools;

/**
 * Sample that illustrates how to send new trade order and listen for trading events
 */
public class OrderSubmitSample extends SampleSupportTools {
    public static void main(String[] args) throws InterruptedException {
        sendRequest(
                (publication) -> {
                    OrderNewRequest request = createNewOrderRequest(Side.BUY, 10, "BTCUSD", 0);
                    publication.onNewOrderRequest(request);
                    System.out.println("New order request was sent " + request.getSourceId() + ':' + request.getOrderId() + ":" + request.getOrderType());
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
        request.setDestinationId(AlphanumericCodec.encode("ICEBERG"));
        request.setExchangeId(AlphanumericCodec.encode("#FILL"));
        request.setSourceId(CLIENT_SOURCE_ID); // Identify order source
        request.setTimestamp(System.currentTimeMillis());
        return request;
    }
}
