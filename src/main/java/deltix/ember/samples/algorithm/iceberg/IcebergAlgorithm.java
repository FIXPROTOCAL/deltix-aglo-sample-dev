package deltix.ember.samples.algorithm.iceberg;

import deltix.anvil.util.Factory;
import deltix.anvil.util.TypeConstants;
import deltix.dfp.Decimal;
import deltix.dfp.Decimal64Utils;
import deltix.ember.message.trade.*;
import deltix.ember.service.algorithm.AlgorithmContext;
import deltix.ember.service.algorithm.ChildOrder;
import deltix.ember.service.algorithm.MarketSubscription;
import deltix.ember.service.algorithm.md.MarketDataProcessor;
import deltix.ember.service.algorithm.md.SimpleInstrumentPrices;
import deltix.ember.service.algorithm.slicer.SlicingAlgorithm;
import deltix.ember.service.oms.cache.OrdersCacheSettings;
import deltix.gflog.Log;
import deltix.gflog.LogFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

@SuppressWarnings("Duplicates")
public class IcebergAlgorithm extends SlicingAlgorithm<IcebergOrder, SimpleInstrumentPrices> {
    private final long defaultOrderDestination;
    private static final Log LOG = LogFactory.getLog(IcebergAlgorithm.class);
    IcebergAlgorithm(final AlgorithmContext context,
                            final OrdersCacheSettings cacheSettings, final long defaultOrderDestination) {
        super(context, cacheSettings);
        this.defaultOrderDestination = defaultOrderDestination;
        LOG.info("IcebergAlgorithm");
        LOG.debug("IcebergAlgorithm");
    }

    @Override
    protected Factory<IcebergOrder> createParentOrderFactory() {
        return IcebergOrder::new;
    }

    @Override
    protected Factory<ChildOrder<IcebergOrder>> createChildOrderFactory() {
        return ChildOrder::new;
    }

    @Override
    protected Function<CharSequence, SimpleInstrumentPrices> createInstrumentInfoFactory() {
        assert false : "This method should never be called";
        return null;
    }

    @Nullable
    @Override
    protected MarketDataProcessor<SimpleInstrumentPrices> createMarketDataProcessor(final MarketSubscription subscription) {
        // We do not need market data.
        return null;
    }


    @Override
    protected CharSequence processNewOrder(IcebergOrder parent, OrderNewRequest request) {
        LOG.info("processNewOrder");
        if (request.getOrderType() != OrderType.CUSTOM && request.getOrderType() != OrderType.LIMIT)
            return "Only CUSTOM or LIMIT orders are expected.";

        if ( ! request.hasLimitPrice())
            return "Limit price must be provided.";

        parent.updateSymbolMetaInfo(getSecurityMetadata().getSMD(parent.getSymbol()));

        if (Decimal64Utils.isGreater(parent.getMinOrderSize(), parent.getMaxOrderQuantity()))
            return "Invalid order size (< minimum order size).";

        return super.processNewOrder(parent, request);
    }


    /**
     * <p>As an example, if the "Order Qty" is to buy 95 lots, "Display Qty" is 10 lots, the algo would enter a child order to buy 10 lots.
     * Once the 10 lots have filled, a new child order is generated to buy an additional 10 and so on until the order is completed,
     * i.e. 95 lots filled. At any point, there should be no more than 10 lots working.</p>
     * <p>
     * <p>If you receive a partial fill of 2 lots, your remaining working qty for the existing order is 8 so the algo would add
     * an additional 2 lots as a separate child order to the back of the queue to work a total of 10
     * (i.e. 2 orders now working, child order #1 working 8 and child order #2 working 2).</p>
     * <p>
     * <p>Monitoring both your fills and outstanding working orders, pseudo-code:
     * <pre>
     * if (Display Qty == 0)
     *   Quantity to work =  OrderQty
     * else
     *   Quantity to work = MIN(OrderQty - FilledQty - OutstandingWkgQty, DisplayQty)
     * </pre>
     * </p>
     */
    @Override
    protected void submitMoreChildrenIfNecessary(IcebergOrder parent) {
        LOG.info("submitMoreChildrenIfNecessary");
        assert isLeader() : "This method should never be called for the follower";

        @Decimal final long remainingQuantity = parent.getRemainingQuantity();
        @Decimal final long quantityOnTheMarket = parent.getMaxRemainingChildrenQuantity(null);
        if (Decimal64Utils.isGreater(remainingQuantity, quantityOnTheMarket)) {
            @Decimal final long displayQuantity = parent.getDisplayQuantity();

            if (!Decimal64Utils.isNaN(displayQuantity) && Decimal64Utils.isGreater(displayQuantity, quantityOnTheMarket)) {
                assert !Decimal64Utils.isZero(displayQuantity);

                @Decimal long childQuantity = Decimal64Utils.subtract(Decimal64Utils.min(remainingQuantity, displayQuantity), quantityOnTheMarket);

                if (Decimal64Utils.isGreater(parent.getMinOrderSize(), childQuantity)) {
                    sendCancelEvent(parent, "Invalid order size (< minimum order size).");
                    return;
                }

                childQuantity = parent.roundOrderQuantity(childQuantity);

                if (Decimal64Utils.isLessOrEqual(childQuantity, Decimal64Utils.ZERO)) {
                    sendCancelEvent(parent, "Invalid order size (< minimum order size).");
                    return;
                }

                assert Decimal64Utils.isPositive(childQuantity);

                final MutableOrderNewRequest childRequest = makeChildOrderRequest(parent);
                childRequest.setOrderType(OrderType.LIMIT);
                childRequest.setQuantity(childQuantity);
                childRequest.setLimitPrice(parent.getWorkingOrder().getLimitPrice());

                submit(parent, childRequest);
            }
        }
    }

    @Override
    protected CharSequence processReplace(final IcebergOrder parent, final OrderReplaceRequest request) {
        LOG.info("processReplace");
        if (request.hasOrderType() && request.getOrderType() != parent.getOrderType())
            return "Order type cannot be modified.";

        if (Decimal64Utils.isGreater(parent.getMinOrderSize(), request.getQuantity()))
            return "Invalid order size (< minimum order size).";

        return super.processReplace(parent, request);
    }

    /**
     * <p>If the algorithm is working multiple orders up to the display size due to getting partials,
     * a price change would trigger only one new order up to the display size to be worked at the new price.
     * Any additional orders would be cancelled. If only one order was working, then only the price would be updated.</p>
     * <p>Example:</p>
     * <p>Before price change [Order Qty = 55, Filled  = 48, Remaining = 7, Working 7]:
     * <ul>
     * <li>Child order #1 working 5</li>
     * <li>Child order #2 working 2</li>
     * <li>Total working qty equals 7</li>
     * </ul>
     * </p>
     * <p>After price change [Order Qty = 55, Filled  = 48, Remaining = 7, Working 7]:
     * <ul>
     * <li>Child order #1 working 7 (price and qty updated)</li>
     * <li>Child order #2 cancelled</li>
     * <li>Total working qty equals 7</li>
     * </ul>
     * </p>
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void commitReplace(IcebergOrder parent) {
        LOG.info("commitReplace");
        if (isLeader()) {
            final List<ChildOrder> children = parent.getActiveChildren();
            // If order has no active children, new child orders submitted from Normal state will use new attributes.
            if (children.size() > 0) {
                assert children.size() == 1 : "At most one child order must remain when order reached CommitingReplace phase.";
                final ChildOrder<IcebergOrder> remainingChild = children.get(0);
                assert parent.getCommitLimitOrder() != null;

                @Decimal long newQuantity = Decimal64Utils.subtract(parent.getCommitLimitOrder().getQuantity(), parent.getTotalExecutedQuantity());
                @Decimal long newDisplayQuantity = parent.getCommitLimitOrder().getDisplayQuantity();
                if (!Decimal64Utils.isNaN(newDisplayQuantity))
                    newQuantity = Decimal64Utils.min(newQuantity, Decimal64Utils.add(newDisplayQuantity, remainingChild.getTotalExecutedQuantity()));

                if (Decimal64Utils.isGreater(parent.getMinOrderSize(), newQuantity)) {
                    sendCancelEvent(parent, "Invalid order size (< minimum order size).");
                    return;
                }

                newQuantity = parent.roundOrderQuantity(newQuantity);

                @Decimal long newPrice = parent.getCommitLimitOrder().getLimitPrice();

                if (Decimal64Utils.isLess(newQuantity, remainingChild.getWorkingQuantity()) || !Decimal64Utils.isEqual(newPrice, remainingChild.getWorkingOrder().getLimitPrice())) {
                    final MutableOrderReplaceRequest replaceRequest = makeReplaceRequest(remainingChild);
                    replaceRequest.setQuantity(newQuantity);
                    replaceRequest.setLimitPrice(newPrice);
                    replaceRequest.setOrderType(OrderType.LIMIT);
                    replace(remainingChild, replaceRequest);
                }
            }
        }
    }


    @Override
    protected MutableOrderNewRequest makeChildOrderRequest(final IcebergOrder icebergOrder) {
        LOG.info("makeChildOrderRequest");
        final MutableOrderNewRequest request = super.makeChildOrderRequest(icebergOrder);
        if (defaultOrderDestination != TypeConstants.LONG_NULL)
            request.setDestinationId(defaultOrderDestination);
        return request;
    }

    @Override
    protected MutableOrderReplaceRequest makeReplaceRequest(final ChildOrder<IcebergOrder> order) {
        LOG.info("MutableOrderReplaceRequest");
        final MutableOrderReplaceRequest request = super.makeReplaceRequest(order);
        if (defaultOrderDestination != TypeConstants.LONG_NULL)
            request.setDestinationId(defaultOrderDestination);
        return request;
    }

    @Override
    protected MutableOrderCancelRequest makeCancelRequest(final ChildOrder<IcebergOrder> order, @Nullable final CharSequence reason) {
        LOG.info("MutableOrderCancelRequest");
        final MutableOrderCancelRequest request = super.makeCancelRequest(order, reason);
        if (defaultOrderDestination != TypeConstants.LONG_NULL)
            request.setDestinationId(defaultOrderDestination);
        return request;
    }

}
