package deltix.ember.samples.algorithm.orderbook;

import deltix.anvil.util.Factory;
import deltix.anvil.util.annotation.Alphanumeric;
import deltix.anvil.util.codec.AlphanumericCodec;
import deltix.dfp.Decimal;
import deltix.dfp.Decimal64Utils;
import deltix.ember.samples.algorithm.iceberg.IcebergAlgorithm;
import deltix.ember.service.algorithm.AbstractAlgorithm;
import deltix.ember.service.algorithm.AlgoOrder;
import deltix.ember.service.algorithm.AlgorithmContext;
import deltix.ember.service.algorithm.ChildOrder;
import deltix.ember.service.algorithm.md.InstrumentData;
import deltix.ember.service.oms.cache.OrdersCacheSettings;
import deltix.qsrv.hf.pub.InstrumentMessage;
import deltix.timebase.api.messages.DataModelType;
import deltix.timebase.api.messages.QuoteSide;
import deltix.timebase.api.messages.universal.PackageHeader;
import rtmath.containers.generated.DecimalLongDecimalLongPair;
import deltix.quoteflow.orderbook.FullOrderBook;
import deltix.quoteflow.orderbook.OrderBookPools;
import deltix.quoteflow.orderbook.OrderBookWaitingSnapshotMode;
import deltix.quoteflow.orderbook.interfaces.ExchangeOrderBook;
import deltix.quoteflow.orderbook.interfaces.OrderBookLevel;
import deltix.quoteflow.orderbook.interfaces.OrderBookList;

import java.util.function.Function;

import deltix.gflog.Log;
import deltix.gflog.LogFactory;

/**
 * Sample that demonstrates how to use Deltix QuoteFlow Order Book in algorithms
 */
public class OrderBookSampleAlgorithm extends AbstractAlgorithm<AlgoOrder, OrderBookSampleAlgorithm.SampleOrderBook> {

    private final OrderBookPools sharedOrderBookPools = new OrderBookPools();
    private final DecimalLongDecimalLongPair longlong = new DecimalLongDecimalLongPair();
    private static final Log LOG = LogFactory.getLog(OrderBookSampleAlgorithm.class);

    public OrderBookSampleAlgorithm(AlgorithmContext context, OrdersCacheSettings cacheSettings) {
        super(context, cacheSettings);
    }

    @Override
     protected Factory<AlgoOrder> createParentOrderFactory() {
        return AlgoOrder::new;
    }

    @Override
    protected Factory<ChildOrder<AlgoOrder>> createChildOrderFactory() {
        return ChildOrder::new;
    }

    /** Tell container we use SampleOrderBook instances to process market data for each instrument */
    @Override
    protected Function<CharSequence, SampleOrderBook> createInstrumentInfoFactory() {
        return SampleOrderBook::new;
    }


    final class SampleOrderBook implements InstrumentData {

        private final FullOrderBook orderBook;

        SampleOrderBook(CharSequence symbol) {
            LOG.info("SampleOrderBook");
            this.orderBook = new FullOrderBook(symbol, OrderBookWaitingSnapshotMode.WAITING_FOR_SNAPSHOT, sharedOrderBookPools, DataModelType.LEVEL_TWO);
        }

        @Override
        public String getSymbol() {
            return (String)orderBook.getSymbol();
        }

        /** Feed order book from algorithm subscription */
        @Override
        public void onMarketMessage(InstrumentMessage message) {
            if (message instanceof PackageHeader)
                orderBook.update((PackageHeader)message);
        }
    }

    /** Here is how you can access current order books for a given symbol */
    private FullOrderBook getOrderBook (CharSequence symbol) {
        SampleOrderBook orderBook = get(symbol);
        if (orderBook != null)
            return orderBook.orderBook;
        return null;
    }

    private static final @Decimal long HUNDRED = Decimal64Utils.fromLong(100);

    /** Very basic illustration of Full Order Book component API. Refer to QuoteFlow  */
    private void iterateAggregatedBook (FullOrderBook aggregatedBook) {
        LOG.info("iterateAggregatedBook");
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("You need %s to buy %s")
                    .withDecimal64(aggregatedBook.getMoneyForVolume(HUNDRED, QuoteSide.ASK, longlong).getSecond())
                    .withDecimal64(HUNDRED);

            LOGGER.trace("VWAP ask:%s bid:%s")
                    .withDecimal64(calculateVWAP(aggregatedBook.getTopLevels(100, QuoteSide.BID)))
                    .withDecimal64(calculateVWAP(aggregatedBook.getTopLevels(100, QuoteSide.ASK)));
        }
    }



    private static final @Alphanumeric long BINANCE_ID = AlphanumericCodec.encode("BINANCE");

    /** Similar sample for per-exchange book API */
    private void inspectExchangeBook (FullOrderBook aggregatedBook) {
        ExchangeOrderBook singleBook = aggregatedBook.getExchange(BINANCE_ID);

        if (LOGGER.isTraceEnabled() && singleBook != null) {
            LOGGER.trace("You need %s to buy %s on BINANCE")
                    .withDecimal64(singleBook.getMoneyForVolume(HUNDRED, QuoteSide.ASK, longlong).getSecond())
                    .withDecimal64(HUNDRED);

            LOGGER.trace("BINANCE VWAP ask:%s bid:%s")
                    .withDecimal64(calculateVWAP(singleBook.getTopLevels(100, QuoteSide.BID)))
                    .withDecimal64(calculateVWAP(singleBook.getTopLevels(100, QuoteSide.ASK)));
        }
    }

    private static @Decimal long calculateVWAP (OrderBookList<OrderBookLevel> levels) {
        LOG.info("calculateVWAP");
        @Decimal long totalValue = Decimal64Utils.ZERO;
        @Decimal long totalSize = Decimal64Utils.ZERO;
        for (int i=0; i < levels.size(); i++) {
            OrderBookLevel level = levels.getObjectAt(i);
            totalValue = Decimal64Utils.add(totalValue, Decimal64Utils.multiply(level.getPrice(), level.getSize()));
            totalSize = Decimal64Utils.add(totalSize, level.getSize());
        }
        if (Decimal64Utils.isZero(totalSize))
            return Decimal64Utils.ZERO;
        return Decimal64Utils.divide(totalValue, totalSize);
    }

}


