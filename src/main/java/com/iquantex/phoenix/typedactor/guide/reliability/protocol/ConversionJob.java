package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

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
