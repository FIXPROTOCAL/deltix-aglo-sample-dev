package deltix.ember.samples.algorithm.iceberg;


import deltix.anvil.util.CharSequenceUtil;
import deltix.dfp.Decimal;
import deltix.dfp.Decimal64Utils;
import deltix.ember.message.smd.InstrumentAttribute;
import deltix.ember.message.smd.InstrumentUpdate;
import deltix.ember.service.algorithm.slicer.SlicingAlgoOrder;
import deltix.util.collections.generated.ObjectList;

public final class IcebergOrder extends SlicingAlgoOrder<IcebergOrderParameters> {
    public IcebergOrder() {
        super(IcebergOrderParameters::new);
    }

    private int orderSizePrecision = 0;
    private @Decimal
    long minOrderSize = Decimal64Utils.ZERO;


    @Decimal
    public long getMinOrderSize() {
        return minOrderSize;
    }

    public void setMinOrderSize(@Decimal long minOrderSize) {
        this.minOrderSize = minOrderSize;
    }

    public int getOrderSizePrecision() {
        return orderSizePrecision;
    }

    public void setOrderSizePrecision(int orderSizePrecision) {
        this.orderSizePrecision = orderSizePrecision;
    }


    public void updateSymbolMetaInfo(final InstrumentUpdate instrumentUpdate) {
        final ObjectList<InstrumentAttribute> attributes = instrumentUpdate.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.size(); i += 1) {
                final InstrumentAttribute attribute = attributes.get(i);
                if (!attribute.hasKey() || !attribute.hasValue())
                    continue;

                if (CharSequenceUtil.equals("minOrderSize", attribute.getKey())) {
                    setMinOrderSize(Decimal64Utils.tryParse(attribute.getValue(), Decimal64Utils.ZERO));
                } else if (CharSequenceUtil.equals("orderSizePrecision", attribute.getKey()) && attribute.getValue() != null) {
                    setOrderSizePrecision(Integer.parseInt(attribute.getValue().toString()));
                }
            }
        }
    }

    @Decimal
    public long getDisplayQuantity() {
        @Decimal final long result = getWorkingOrder().getDisplayQuantity();
        return Decimal64Utils.isNaN(result) || Decimal64Utils.isZero(result) ? getWorkingQuantity() : result;
    }

    /*
    Round quantity using min order size and order size precision
     */
    public long roundOrderQuantity(@Decimal long quantity) {
        // round active quantity using order size precision
        // round active quantity to the nearest value multiple of minimum order quantity
        if (Decimal64Utils.isZero(getMinOrderSize()))
            return Decimal64Utils.roundTowardsNegativeInfinity(quantity, Decimal64Utils.fromFixedPoint(1, getOrderSizePrecision()));

        return Decimal64Utils.roundToNearestTiesAwayFromZero(Decimal64Utils.roundTowardsNegativeInfinity(quantity, Decimal64Utils.fromFixedPoint(1, getOrderSizePrecision())), getMinOrderSize());
    }

    @Override
    public void clear() {
        super.clear();
    }
}

