package com.iquantex.phoenix.typedactor.guide.protocol;

/** 这里定义了抽象的消息接口，继承了序列化接口 {@link CborSerializable} ,该接口在akka配置中配置成JSON序列化 */
public interface Message extends CborSerializable {}
