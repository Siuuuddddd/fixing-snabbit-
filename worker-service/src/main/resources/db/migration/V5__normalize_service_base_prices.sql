UPDATE worker_skill SET base_price_per_hour_minor = CASE service_code
    WHEN 'HOUSE_CLEANING' THEN 18000
    WHEN 'DISH_WASHING' THEN 12000
    WHEN 'LAUNDRY' THEN 14000
    WHEN 'TOILET_CLEANING' THEN 16000
    ELSE base_price_per_hour_minor
END;
