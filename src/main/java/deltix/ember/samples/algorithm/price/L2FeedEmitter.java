package deltix.ember.samples.algorithm.price;

import deltix.anvil.util.AsciiStringBuilder;
import deltix.anvil.util.ObjectPool;
import deltix.anvil.util.TypeConstants;
import deltix.anvil.util.annotation.Alphanumeric;
import deltix.anvil.util.clock.EpochClock;
import deltix.dfp.Decimal;
import deltix.ember.message.trade.Side;
import deltix.ember.util.CharSeqCounter;
import deltix.gflog.Log;
import deltix.gflog.LogLevel;
import deltix.qsrv.hf.pub.InstrumentType;
import deltix.timebase.api.messages.*;
import deltix.timebase.api.messages.universal.*;
import deltix.util.collections.generated.ObjectArrayList;

import java.util.function.Consumer;

/** Helper class that generates market data in Deltix "universal" market data format. See Universal Market Data format documentation for more information */
public class L2FeedEmitter {
    private final Log logger;

    private final @Alphanumeric long exchangeId;

    private final Consumer<PackageHeader> consumer;
    private final ObjectPool<L2EntryNew> inserts;
    private final ObjectPool<L2EntryUpdate> updates;
    private final ObjectPool<TradeEntry> trades;
    private final PackageHeader packageHeader;
    private final ObjectArrayList<BaseEntryInfo> packageEntries = new ObjectArrayList<>();
    private final EpochClock clock;


    public L2FeedEmitter(@Alphanumeric long exchangeId, int initialCapacityOfEachPool, Consumer<PackageHeader> consumer, EpochClock clock, Log logger) {
        this.inserts = new ObjectPool<>(initialCapacityOfEachPool, this::makeInsert);
        this.updates = new ObjectPool<>(initialCapacityOfEachPool, this::makeUpdate);
        this.trades = new ObjectPool<>(initialCapacityOfEachPool, this::makeTrade);
        this.consumer = consumer;
        this.clock = clock;
        this.logger = logger.isEnabled(LogLevel.TRACE) ? logger : null;
        this.exchangeId = exchangeId;

        packageHeader = new PackageHeader();
        packageHeader.setInstrumentType(InstrumentType.FX); //TODO
        packageHeader.setEntries(packageEntries);
        packageHeader.setSourceId(exchangeId);
    }


    public void init (CharSequence symbol, PackageType packageType) {
        packageHeader.setSymbol(symbol);
        packageHeader.setPackageType(packageType);
        packageHeader.setTimeStampMs(clock.time());
    }

    public void flush () {
        consumer.accept(packageHeader);
        if (logger != null)
            logger.trace("L3>%s").with(packageHeader);
        reset();
    }

    public void reset () {
        final int cnt = packageEntries.size();
        for (int i=0; i < cnt; i++) {
            BaseEntryInfo entry = packageEntries.get(i);
            if (entry instanceof L2EntryNew)
                inserts.release((L2EntryNew) entry);
            else
            if (entry instanceof L2EntryUpdate)
                updates.release((L2EntryUpdate) entry);
            else
                trades.release((TradeEntry) entry);
        }
        packageEntries.clear();
        packageHeader.setTimeStampMs(TypeConstants.TIMESTAMP_NULL);
        packageHeader.setPackageType(null);
        packageHeader.setSymbol(null);
    }

    public void recordTrade(Side aggressorSide, @Decimal long tradeQuantity, @Decimal long tradePrice, CharSequence tradeId, CharSequence orderId) {
        TradeEntry trade = trades.borrow();
        trade.setPrice(tradePrice);
        trade.setSize(tradeQuantity);
        trade.setSide(aggressorSide == Side.BUY ? AggressorSide.BUY : AggressorSide.SELL);
        ((AsciiStringBuilder)trade.getMatchId()).clear().append(tradeId);
        if (trade.getSide() == AggressorSide.BUY) {
            ((AsciiStringBuilder) trade.getBuyerOrderId()).clear();
            ((AsciiStringBuilder) trade.getSellerOrderId()).clear().append(orderId);
        } else {
            ((AsciiStringBuilder) trade.getBuyerOrderId()).clear().append(orderId);
            ((AsciiStringBuilder) trade.getSellerOrderId()).clear();
        }
        packageEntries.add(trade);
    }

    public void recordEntryInsert(QuoteSide side, @Decimal long quantity, @Decimal long price, short level) {
        L2EntryNew insert = inserts.borrow();
        insert.setPrice(price);
        insert.setSize(quantity);
        insert.setSide(side);
        insert.setLevel(level);
        //((AsciiStringBuilder)insert.getQuoteId()).clear().append(quoteId);
        packageEntries.add(insert);
    }

    public void recordEntryUpdate(QuoteSide side, @Decimal long quantity, @Decimal long price, short level) {
        L2EntryUpdate update = updates.borrow();
        update.setPrice(price);
        update.setSize(quantity);
        update.setSide(side);
        update.setAction(BookUpdateAction.UPDATE);
        update.setLevel(level);
        //((AsciiStringBuilder)update.getQuoteId()).clear().append(quoteId);
        packageEntries.add(update);
    }

    public void recordEntryDelete(QuoteSide side, @Decimal long quantity, @Decimal long price, short level) {
        L2EntryUpdate delete = updates.borrow();
        delete.setPrice(price);
        delete.setSize(quantity);
        delete.setSide(side);
        delete.setAction(BookUpdateAction.DELETE);
        delete.setLevel(level);
        //((AsciiStringBuilder)delete.getQuoteId()).clear().append(quoteId);
        packageEntries.add(delete);
    }

//    void publishSnapshot(SimpleL2Book orderBook, int maxDepth, MessageChannel<MarketMessageInfo> out) {
//        assert packageEntries.isEmpty() : "clean package";
//        packageHeader.setPackageType(PackageType.VENDOR_SNAPSHOT); // temp change
//
//        @Alphanumeric final long exchangeId = packageHeader.getSourceId();
//
//        List<SimpleLevel> asks = orderBook.getExchange(exchangeId).getSide(QuoteSide.ASK);
//        int depth = Math.min(asks.size(), maxDepth);
//        for (int i=0; i < depth; i++)
//            append(i, asks.get(i), QuoteSide.ASK);
//        List<SimpleLevel> bids = orderBook.getExchange(exchangeId).getSide(QuoteSide.BID);
//        depth = Math.min(bids.size(), maxDepth);
//        for (int i=0; i < depth; i++)
//            append(i, bids.get(i), QuoteSide.BID);
//
//        packageHeader.setTimeStampMs(clock.time());
//        out.send(packageHeader);
//        reset();
//        packageHeader.setPackageType(PackageType.INCREMENTAL_UPDATE); // restore
//    }
//
//    private void append(int level, SimpleLevel entry, QuoteSide side) {
//        L2EntryNew insert = snapshots.borrow();
//        insert.setLevel((short) level);
//        insert.setSide(side);
//        insert.setPrice(entry.price);
//        insert.setSize(entry.size);
//        ((AsciiStringBuilder)insert.getQuoteId()).clear();
//        packageEntries.add(insert);
//    }


    private L2EntryNew makeInsert () {
        L2EntryNew result = new L2EntryNew ();
        result.setNumberOfOrders(1);
        //result.setQuoteId(new AsciiStringBuilder(CharSeqCounter.WIDTH));
        result.setExchangeId(exchangeId);
        return result;
    }

    private L2EntryUpdate makeUpdate () {
        L2EntryUpdate result = new L2EntryUpdate ();
        result.setNumberOfOrders(1);
        //result.setQuoteId(new AsciiStringBuilder(CharSeqCounter.WIDTH));
        result.setExchangeId(exchangeId);
        return result;
    }

    private TradeEntry makeTrade () {
        TradeEntry result = new TradeEntry ();
        result.setTradeType(TradeType.REGULAR_TRADE);
        result.setMatchId(new AsciiStringBuilder(CharSeqCounter.WIDTH));
        result.setBuyerOrderId(new AsciiStringBuilder(32));
        result.setSellerOrderId(new AsciiStringBuilder(32));
        result.setExchangeId(exchangeId);
        return result;
    }
}

