package deltix.ember.samples.algorithm.price;

import org.junit.Test;

public class Test_SynthPriceAlgorithmMock extends OrderBookProducingAlgorithmTest<SynthPriceAlgorithm> {

    public Test_SynthPriceAlgorithmMock () {
        super ("XBTJPY");
    }

    @Override
    protected SynthPriceAlgorithm createAlgorithm() {
        final SynthPriceAlgorithm algorithm = new SynthPriceAlgorithm(getAlgorithmContext(), getCacheSettings(), OUTPUT_STREAM_KEY);
        defineCurrencyInstrument("BTCUSD", algorithm);
        defineCurrencyInstrument("LTCUSD", algorithm);
        defineCurrencyInstrument("USDJPY", algorithm);
        defineCurrencyInstrument("BTCJPY", algorithm);
        defineCurrencyInstrument("USDCHF", algorithm);
        defineCurrencyInstrument("LTCCHF", algorithm);
        return algorithm;
    }

    @Test
    public void simplePriceOuput() {
        simulateOrderBook(
                "BTCUSD",
                EXCHANGE_ID,
                "7 @ 4025.02",
                "3   @ 4025.01",
                "1   @ 4025.00",
                "---------------",
                "0.5 @ 4024.010",
                "1   @ 4024.004",
                "2   @ 4023.001"
        );

        verifyNoOutputPrices();

        // Now we define midpoint
        simulateOrderBook(
                "USDJPY",
                EXCHANGE_ID,
                "2000 @ 110.33002",
                "1000 @ 110.33001",
                "---------------",
                "1000 @ 110.33000",
                "2000 @ 110.32999"
        );

        assertOutputPriceMessageCount(1);

        assertOrderBook("10 @ 444079.925075075\n" +
                "5 @ 444079.37342505\n" +
                "3 @ 444079.0056583667\n" +
                "1.5 @ 444078.270125\n" +
                "---------------\n" +
                "0.5 @ 443969.04342005\n" +
                "1 @ 443968.712430035\n" +
                "3 @ 443913.1612725175");

        // Let's send update but keep midpoint unchanged
        simulateOrderBook(
                "USDJPY",
                EXCHANGE_ID,
                "2000 @ 110.55555",
                "1000 @ 110.33001",
                "---------------",
                "1000 @ 110.33000",
                "2000 @ 110.32222"
        );

        // there should be no new rates published
        assertOutputPriceMessageCount(1);


        // Let's change midpoint and verify new rates
        simulateOrderBook(
                "USDJPY",
                EXCHANGE_ID,
                "2000 @ 110.55555",
                "1000 @ 110.33000",
                "---------------",
                "1000 @ 110.32999",
                "2000 @ 110.32222"
        );

        assertOutputPriceMessageCount(2);
        assertOrderBook("10 @ 444079.884824925\n" +
                "5 @ 444079.33317495\n" +
                "3 @ 444078.9654083\n" +
                "1.5 @ 444078.229875\n" +
                "---------------\n" +
                "0.5 @ 443969.00317995\n" +
                "1 @ 443968.672189965\n" +
                "3 @ 443913.1210374825");

        // let's change base order book and verify update
        simulateOrderBook(
                "BTCUSD",
                EXCHANGE_ID,
                "7 @ 4025.02",
                "3   @ 4025.01",
                "1   @ 4025.00",
                "---------------",
                "1   @ 4024.004",
                "5   @ 4023.001"
        );

        assertOutputPriceMessageCount(3);
        assertOrderBook("10 @ 444079.884824925\n" +
                "5 @ 444079.33317495\n" +
                "3 @ 444078.9654083\n" +
                "1.5 @ 444078.229875\n" +
                "---------------\n" +
                "1.5 @ 443968.34119998\n" +
                "3 @ 443894.56720999\n" +
                "5 @ 443879.812411992");
    }


}
