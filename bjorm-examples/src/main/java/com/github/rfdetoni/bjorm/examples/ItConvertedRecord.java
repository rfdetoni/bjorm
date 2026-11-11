package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Converted;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

@Table("bjorm_it_converted")
public record ItConvertedRecord(@Id UUID id,
        @Converted(MoneyValueConverter.class) MoneyValue amount,
        String label) {}
