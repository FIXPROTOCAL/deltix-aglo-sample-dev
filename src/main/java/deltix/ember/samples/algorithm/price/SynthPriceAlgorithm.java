package deltix.ember.samples.algorithm.price;

import deltix.anvil.util.AsciiStringBuilder;
import deltix.anvil.util.CloseHelper;
import deltix.anvil.util.Factory;
import deltix.anvil.util.annotation.Alphanumeric;
import deltix.data.stream.MessageChannel;
import deltix.dfp.Decimal;
import deltix.dfp.Decimal64Utils;
import deltix.ember.message.smd.CurrencyUpdate;
import deltix.ember.message.smd.InstrumentType;
import deltix.ember.service.algorithm.AbstractAlgorithm;
import deltix.ember.service.algorithm.AlgoOrder;
import deltix.ember.service.algorithm.AlgorithmContext;
import deltix.ember.service.algorithm.ChildOrder;
import deltix.ember.service.algorithm.md.InstrumentData;
import deltix.ember.service.data.InstrumentInfo;
import deltix.ember.service.oms.cache.OrdersCacheSettings;
import deltix.gflog.Log;
import deltix.qsrv.hf.pub.InstrumentMessage;
import deltix.timebase.api.messages.DataModelType;
import deltix.timebase.api.messages.MarketMessageInfo;
import deltix.timebase.api.messages.QuoteSide;
import deltix.timebase.api.messages.universal.PackageHeader;
import deltix.timebase.api.messages.universal.PackageType;
import rtmath.containers.generated.DecimalLongDecimalLongPair;
import deltix.quoteflow.orderbook.FullOrderBook;
import deltix.quoteflow.orderbook.OrderBookPools;
import deltix.quoteflow.orderbook.OrderBookWaitingSnapshotMode;
import deltix.quoteflow.orderbook.interfaces.FullBook;
import deltix.quoteflow.orderbook.interfaces.OrderBookLevel;
import deltix.quoteflow.orderbook.interfaces.OrderBookList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;



/**
 * Sample algorithm that illustrates how to generate Level 2 market data.
 * More specifically how to write market data containing order book snapshots into TimeBase stream.
 *
 * Sample uses crypto-fiat currency pair (e.g. BTCUSD) as a source of order book rates.
 * To produce synthetic crypto-fiat pair sample uses FX rate that converts source book currency into another once (e.g. USDJPY).
 * In described case algorithm generates BTCJPY rates for given number of tiers.
 *
 * The first version has many limitations (this is just a sample). For example there is no support for inverted conversions like
 * BTCUSD + GBPUSD = BTCGBP
 *
 *
 */
public class SynthPriceAlgorithm extends AbstractAlgorithm<AlgoOrder, SynthPriceAlgorithm.OrderBookData> {

    private final Log logger;

    // e.g. 0.5 BTC, 1 BTC, 3 BTC , 5BTC, 10BTC
    private static final @Decimal long [] TIER_SIZES = {
            Decimal64Utils.parse("0.5"),
            Decimal64Utils.parse("1.0"),
            Decimal64Utils.parse("3.0"),
            Decimal64Utils.parse("5.0"),
            Decimal64Utils.parse("10.0")
    };

    private final OrderBookPools sharedOrderBookPools = new OrderBookPools();
    private MessageChannel<MarketMessageInfo> outputChannel;
    private final L2FeedEmitter feedEmitter;

    SynthPriceAlgorithm(AlgorithmContext context, OrdersCacheSettings cacheSettings, String outputStreamKey) {
        super(context, cacheSettings);

        logger = context.getLogger();
        outputChannel = openOutputChannel(outputStreamKey);

        feedEmitter = new L2FeedEmitter(context.getId(), TIER_SIZES.length*2, this::publishMarketData, context.getClock(), context.getLogger());
    }

    @Override
    public void open() {
        super.open();

        //TODO: Move to configuration
        // Define what derived rates we will be publishing
        defineRates ("BTCUSD", "USDJPY", "BTCJPY");
//        defineRates ("BTCUSD", "USDCHF", "BTCCHF");
//        defineRates ("LTCUSD", "USDJPY", "LTCJPY");
    }


    @Override
    public void close() {
        CloseHelper.close(outputChannel);
    }

    /**
     * Links up two instruments as rate source and derived one BTCUSD + USD/JPY produces BTCJPY rate
     * @param sourceSymbol identifies instrumentMetadata that will be used to define (e.g. BTCUSD)
     * @param derivedRatesSymbol identifies instrumentMetadata that will be used for derived rate (e.g. USDJPY)
     */
    private void defineRates(String sourceSymbol, String derivedRatesSymbol, String resultRateSymbol) {
        OrderBookData source = getOrCreate(sourceSymbol, InstrumentType.FX);
        OrderBookData derivative = getOrCreate(derivedRatesSymbol, InstrumentType.FX);
        derivative.addRatesSource(source, resultRateSymbol);
    }


    /// region Per-instrumentMetadata market data processor

    /** Tell container we use SampleOrderBook instances to process market data for each instrumentMetadata */
    @Override
    protected Function<CharSequence, OrderBookData> createInstrumentInfoFactory() {
        return OrderBookData::new;
    }

    /** This class keep per-instrumentMetadata state. */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    class OrderBookData implements InstrumentData {
        /** Instrument containing source order book that this instrumentMetadata uses to produce rates */
        private final List<OrderBookData> rateSources = new ArrayList<>();
        /** References instruments that derive their rate from this instrumentMetadata's order book */
        private final List<OrderBookData> derivedRates = new ArrayList<>();
        private final CurrencyUpdate instrumentMetadata;
        private String resultRateSymbol;
        private @Decimal long midpoint = Decimal64Utils.NULL;
        private final FullBook orderBook; // current order book
        private final DecimalLongDecimalLongPair longlong = new DecimalLongDecimalLongPair(); // used for OrderBook API function calls


        OrderBookData(CharSequence symbol) {
            this.orderBook = new FullOrderBook(symbol, OrderBookWaitingSnapshotMode.WAITING_FOR_SNAPSHOT, sharedOrderBookPools, DataModelType.LEVEL_TWO);

            InstrumentInfo instrumentInfo = getSecurityMetadata().get(symbol);
            if (instrumentInfo == null)
                throw new IllegalArgumentException("Symbol " + symbol + " is not defined in Security Metadata");

            this.instrumentMetadata = (CurrencyUpdate) instrumentInfo.getInstrument();

        }

        private CurrencyUpdate getCurrencyMetadata () {
            return instrumentMetadata;
        }

        @Override
        public String getSymbol() {
            return (String)orderBook.getSymbol();
        }

        void addRatesSource(OrderBookData source, String resultRateSymbol) {
            this.rateSources.add(source);
            this.resultRateSymbol = resultRateSymbol;
            source.derivedRates.add(this);
        }

        /** Called by container each time input market data has changed (usually incremental market data feed in "universal" format) */
        @Override
        public void onMarketMessage(InstrumentMessage message) {
            if (message instanceof PackageHeader) {
                // apply input message to current order book
                orderBook.update((PackageHeader) message);

                // republish rates: if midpoint changed and we have rateSources
                if ( ! rateSources.isEmpty()) {
                    @Decimal long newMidpoint = calculateMidpoint();
                    if (Decimal64Utils.isNotEqual(midpoint, newMidpoint)) {
                        midpoint = newMidpoint;
                        publishRates();
                    }
                }

                // republish rates: this book changed => republish derived rates
                for (int i=0; i < derivedRates.size(); i++) {
                    derivedRates.get(i).publishRates();
                }
            }
        }

        private void publishRates() {
            if ( ! Decimal64Utils.isNull(midpoint)) {
                for (int i = 0; i < rateSources.size(); i++) {
                    OrderBookData ratesSource = rateSources.get(i);
                    feedEmitter.init(formatCurrencySymbol(ratesSource, this), PackageType.VENDOR_SNAPSHOT);
                    publishRates(ratesSource, QuoteSide.ASK);
                    publishRates(ratesSource, QuoteSide.BID);
                    feedEmitter.flush();
                }
            }
        }



        private void publishRates(OrderBookData ratesSource, QuoteSide side) {
            for (int tier = 0; tier < TIER_SIZES.length; tier++) {
                @Decimal long tierSize = TIER_SIZES[tier];

                // Compute tier price: this function returns tuple {availableVolume, money}
                // Note: here we use 'getMoneyForVolume' OrderBook API function. This function traverses order book from the top.
                // For production-grade code we may want to traverse this book once instead doing that on each iteration
                DecimalLongDecimalLongPair result = ratesSource.orderBook.getMoneyForVolume(tierSize, side, longlong);
                if (Decimal64Utils.isLess(result.getSecond(), tierSize))
                    break; // not enough liquidity

                @Decimal long tierPrice = Decimal64Utils.multiply(Decimal64Utils.divide(result.getFirst(), tierSize), midpoint);

                feedEmitter.recordEntryInsert(side, tierSize, tierPrice, (short) tier);
            }
        }

        private @Decimal long calculateMidpoint () {
            @Decimal long bestAsk = getBestPrice(QuoteSide.ASK);
            @Decimal long bestBid = getBestPrice(QuoteSide.BID);
            if (Decimal64Utils.isNull(bestAsk) || Decimal64Utils.isNull(bestBid))
                return Decimal64Utils.NULL;
            return Decimal64Utils.divideByInteger(Decimal64Utils.add(bestAsk, bestBid), 2);
        }

        private @Decimal long getBestPrice(QuoteSide side) {
            OrderBookList<OrderBookLevel> best = orderBook.getTopLevels(1, side);
            return (best != null && best.size() > 0) ? best.getObjectAt(0).getPrice() : Decimal64Utils.NULL;
        }
    }

    /// endregion

    /// region Market data

    @SuppressWarnings("unchecked")
    private MessageChannel<MarketMessageInfo> openOutputChannel(String streamKey) {
        if (streamKey != null)
            return (MessageChannel) context.createOutputChannel(streamKey, PackageHeader.class);

        return null;
    }


    private void publishMarketData(MarketMessageInfo message) {
        if (outputChannel != null && isLeader()) {
            try {
                outputChannel.send(message);
            } catch (Exception e) {
                logger.fatal("Output channel is closed! Stopping market data feed: %s").with(e);
                CloseHelper.close(outputChannel); // Just safety check. This ensures proper close.
                outputChannel = null;
            }
        }
    }

    /// endregion

    /// region non-essential stuff

    @Override
    protected Factory<AlgoOrder> createParentOrderFactory() {
        return AlgoOrder::new; // this sample doesn't trade
    }

    @Override
    protected Factory<ChildOrder<AlgoOrder>> createChildOrderFactory() {
        return ChildOrder::new; // this sample doesn't trade
    }

    private final AsciiStringBuilder rateSymbolBuffer = new AsciiStringBuilder(8);

    private CharSequence formatCurrencySymbol (OrderBookData ratesSource, OrderBookData derived) {
        @Alphanumeric long baseCurrencyISOCode = ratesSource.getCurrencyMetadata().getBaseCurrency();
        @Alphanumeric long termCurrencyISOCode = derived.getCurrencyMetadata().getCurrency();
        rateSymbolBuffer.clear().appendAlphanumeric(baseCurrencyISOCode).appendAlphanumeric(termCurrencyISOCode);
        return rateSymbolBuffer; // OK to return single shared buffer providing we copy result immediately
    }

    /// endregion
}

