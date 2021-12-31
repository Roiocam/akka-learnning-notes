package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 转换 Job 类.
 *
 * @author AndyChen
 */
@AllArgsConstructor
@Getter
public class ConversionJob {

    private final UUID resultId;
    private final String fromFormat;
    private final String toFormat;
    private final byte[] image;

}
