package com.impassive.pay;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.impassive.pay.cmd.CreatePaySignCmd;
import com.impassive.pay.entity.AliConstant;
import com.impassive.pay.entity.AliTradeStatus;
import com.impassive.pay.entity.NotifyInfo;
import com.impassive.pay.exception.ApplySignException;
import com.impassive.pay.result.AliTradeInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author impassive
 */
@Slf4j
public class AliPayClient {

  private final AliPayProperties aliPayProperties;
  private final DefaultAlipayClient alipayClient;

  public AliPayClient(AliPayProperties aliPayProperties) throws AlipayApiException {
    this.aliPayProperties = aliPayProperties;
    AlipayConfig config = new AlipayConfig();
    config.setServerUrl("https://openapi.alipay.com/gateway.do");
    config.setAlipayPublicKey(aliPayProperties.getAliPublicKey());
    config.setAppId(aliPayProperties.getAliAppId());
    config.setPrivateKey(aliPayProperties.getAliPrivateKey());
    config.setEncryptKey(aliPayProperties.getEncryptKey());
    config.setCharset("UTF-8");
    config.setSignType("RSA2");
    config.setFormat("json");
    this.alipayClient = new DefaultAlipayClient(config);
  }

  public String applyPaymentSign(CreatePaySignCmd createPaySignCmd) {
    if (createPaySignCmd == null || createPaySignCmd.checkIsIllegal()) {
      throw new IllegalArgumentException("apply sign param is illegal");
    }
    try {
      AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
      AlipayTradeAppPayModel model = buildOnceRequest(createPaySignCmd);
      request.setBizModel(model);
      request.setNotifyUrl(aliPayProperties.getPayNotifyUrl());
      request.setNeedEncrypt(false);
      AlipayTradeAppPayResponse response = alipayClient.sdkExecute(request);
      if (!response.isSuccess()) {
        log.error("?????? ???????????? ??? {},{}", response, createPaySignCmd);
        throw new ApplySignException("apply ali pay sign failed");
      }
      return response.getBody();
    } catch (Exception e) {
      log.error("AliPay create trade error, ", e);
      throw new ApplySignException(e);
    }
  }

  public AliTradeInfo queryTradeInfo(String paymentNo) {
    try {
      AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
      JSONObject bizContent = new JSONObject();
      // ?????????????????????ID
      bizContent.put("out_trade_no", paymentNo);
      request.setBizContent(bizContent.toString());
      AlipayTradeQueryResponse execute = alipayClient.execute(request);
      if (!execute.isSuccess()) {
        log.error("query has failed : {}", execute.getBody());
        throw new RuntimeException("query has failed");
      }
      String aliStatus = execute.getTradeStatus();
      AliTradeStatus aliTradeStatus = AliTradeStatus.of(aliStatus);
      if (aliTradeStatus == null) {
        log.error("trade status is null : {}, {}, {}", paymentNo, execute, aliStatus);
        return null;
      }
      return convertToTradeInfo(execute);
    } catch (Exception e) {
      log.error("query alipay result has error : {}", paymentNo, e);
      throw new RuntimeException(e);
    }
  }

  public AliTradeInfo parseAliServicePayCallback(String body) {
    if (StringUtils.isEmpty(body)) {
      log.error("notifyType is null : {}", body);
      throw new IllegalArgumentException("Ali ????????????????????????");
    }
    // 1. ???????????????
    NotifyInfo notifyInfo = NotifyInfo.fromJson(body);
    if (notifyInfo == null) {
      log.error("parse ali callback has error : {}", body);
      throw new RuntimeException("?????? ????????? ??????????????????");
    }
    // 2. ???????????????
    try {
      boolean signVerified = AlipaySignature.rsaCheckV1(
          notifyInfo.getParams(),
          aliPayProperties.getAliPublicKey(),
          AliConstant.CHARSET,
          AliConstant.SIGN_TYPE
      );
      if (!signVerified) {
        log.error("sign verified has failed : {}", body);
        throw new IllegalStateException("??????????????????");
      }
    } catch (Exception e) {
      log.error("sign verified has failed : {}", body);
      throw new IllegalStateException("??????????????????");
    }

    // 3. ?????????????????????????????????
    return queryTradeInfo(notifyInfo.paymentNo());
  }




  /* ========================================= */

  private AlipayTradeAppPayModel buildOnceRequest(CreatePaySignCmd createPaySignCmd) {
    AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
    // ????????????
    model.setSubject(createPaySignCmd.getInventoryName());
    // ????????????
    // ?????? id -----> trade_no	????????????????????????????????????
    model.setOutTradeNo(createPaySignCmd.getPaymentNo());
    // ?????????????????????????????????????????????????????????5m?????????5m?????????????????????5m????????? ??????????????????
    model.setTimeoutExpress(aliPayProperties.getOrderTimeOutExpress().toMinutes() + "m");
    // ??????????????????????????????????????????????????????????????????[0.01,100000000]
    model.setTotalAmount(toAliAmount(createPaySignCmd.getAmount().asFen()));
    // ????????????
    model.setProductCode(createPaySignCmd.getProductCode().getValue());
    model.setSellerId(aliPayProperties.getSellerId());
    return model;
  }


  private String toAliAmount(Integer amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("Amount can not be negative " + amount);
    }
    return Double.valueOf(amount / 100.0).toString();
  }


  private AliTradeInfo convertToTradeInfo(AlipayTradeQueryResponse execute) {
    AliTradeInfo aliTradeInfo = new AliTradeInfo();
    aliTradeInfo.setAlipayStoreId(execute.getAlipayStoreId());
    aliTradeInfo.setAlipaySubMerchantId(execute.getAlipaySubMerchantId());
    aliTradeInfo.setAuthTradePayMode(execute.getAuthTradePayMode());
    aliTradeInfo.setBody(execute.getBody());
    aliTradeInfo.setBuyerLogonId(execute.getBuyerLogonId());
    aliTradeInfo.setBuyerOpenId(execute.getBuyerOpenId());
    aliTradeInfo.setBuyerPayAmount(execute.getBuyerPayAmount());
    aliTradeInfo.setBuyerUserId(execute.getBuyerUserId());
    aliTradeInfo.setBuyerUserName(execute.getBuyerUserName());
    aliTradeInfo.setBuyerUserType(execute.getBuyerUserType());
    aliTradeInfo.setChargeAmount(execute.getChargeAmount());
    aliTradeInfo.setOutTradeNo(execute.getOutTradeNo());
    aliTradeInfo.setPayAmount(execute.getPayAmount());
    aliTradeInfo.setPayCurrency(execute.getPayCurrency());
    aliTradeInfo.setPointAmount(execute.getPointAmount());
    aliTradeInfo.setReceiptAmount(execute.getReceiptAmount());
    aliTradeInfo.setReceiptCurrencyType(execute.getReceiptCurrencyType());
    aliTradeInfo.setSendPayDate(execute.getSendPayDate());
    aliTradeInfo.setSettleAmount(execute.getSettleAmount());
    aliTradeInfo.setSettleCurrency(execute.getSettleCurrency());
    aliTradeInfo.setStoreId(execute.getStoreId());
    aliTradeInfo.setStoreName(execute.getStoreName());
    aliTradeInfo.setSubject(execute.getSubject());
    aliTradeInfo.setTotalAmount(execute.getTotalAmount());
    aliTradeInfo.setTradeNo(execute.getTradeNo());
    aliTradeInfo.setTradeStatus(AliTradeStatus.of(execute.getTradeStatus()));
    return aliTradeInfo;
  }

}
