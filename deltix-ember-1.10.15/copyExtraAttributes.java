void copyExtraAttributes(OrderEntryRequest request, boolean modify, EpochClock clock) {
        // parse parameters
        LOG.info("copyExtraAttributes:"+request);
        LOG.info("copyExtraAttributes:"+request.hasAttributes());
        if (request.hasAttributes()) {
            ObjectList<CustomAttribute> attributes = request.getAttributes();
//            LOG.info("ObjectList<CustomAttribute>"+attributes);
            for (int i = 0, size = attributes.size(); i < size; i++) {
                CustomAttribute attribute = attributes.get(i);
//                LOG.info("attribute:"+attribute);
                switch (attribute.getKey()) {
                    case START_TIME_ATTRIBUTE_KEY:
                        startTime = TimestampParser.parseTimestamp(attribute.getValue());
                        break;
                    case END_TIME_ATTRIBUTE_KEY:
                        endTime = TimestampParser.parseTimestamp(attribute.getValue());
                        break;
                    case DRIP_PERCENTAGE_ATTRIBUTE_KEY:
                        dripPercentage = CharSequenceParser.parseDouble(attribute.getValue());
                        break;
                    case DURATION_ATTRIBUTE_KEY:
                        duration = OrderAttributesParser.parseDuration(DURATION_ATTRIBUTE_KEY, attribute.getValue());
                        break;
                    case ACTIVE_TOLERANCE_PERCENTAGE:
                        activeTolerancePercentage = CharSequenceParser.parseDouble(attribute.getValue());
                        break;
                }
            }
        }

        if (startTime == TypeConstants.TIMESTAMP_NULL)
            startTime = clock.time();



        if (endTime == TypeConstants.TIMESTAMP_NULL) {
            if (duration == TypeConstants.TIMESTAMP_NULL)
                throw new InvalidOrderException("Missing end time or duration parameters");
            endTime = startTime + duration;
        }


        // Validate parameters
        if (dripPercentage == 0)
            throw new InvalidOrderException("Parameter dripPercentage is missing");
        if (startTime >= endTime)
            throw new InvalidOrderException("Empty time range");
        if (activeTolerancePercentage != 0 && activeTolerancePercentage < dripPercentage)
            throw new InvalidOrderException("Parameters activeTolerancePercentage < dripPercentage");

    }