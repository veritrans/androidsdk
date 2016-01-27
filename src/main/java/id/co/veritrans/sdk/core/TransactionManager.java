package id.co.veritrans.sdk.core;

import android.app.Activity;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.GsonBuilder;

import id.co.veritrans.sdk.R;
import id.co.veritrans.sdk.callbacks.DeleteCardCallback;
import id.co.veritrans.sdk.callbacks.GetOffersCallback;
import id.co.veritrans.sdk.callbacks.PaymentStatusCallback;
import id.co.veritrans.sdk.callbacks.SavedCardCallback;
import id.co.veritrans.sdk.callbacks.TokenCallBack;
import id.co.veritrans.sdk.callbacks.TransactionCallback;
import id.co.veritrans.sdk.models.BBMMoneyRequestModel;
import id.co.veritrans.sdk.models.CIMBClickPayModel;
import id.co.veritrans.sdk.models.CardResponse;
import id.co.veritrans.sdk.models.CardTokenRequest;
import id.co.veritrans.sdk.models.CardTransfer;
import id.co.veritrans.sdk.models.DeleteCardResponse;
import id.co.veritrans.sdk.models.EpayBriTransfer;
import id.co.veritrans.sdk.models.GetOffersResponseModel;
import id.co.veritrans.sdk.models.IndomaretRequestModel;
import id.co.veritrans.sdk.models.IndosatDompetkuRequest;
import id.co.veritrans.sdk.models.MandiriBillPayTransferModel;
import id.co.veritrans.sdk.models.MandiriClickPayRequestModel;
import id.co.veritrans.sdk.models.MandiriECashModel;
import id.co.veritrans.sdk.models.PermataBankTransfer;
import id.co.veritrans.sdk.models.TokenDetailsResponse;
import id.co.veritrans.sdk.models.TransactionResponse;
import id.co.veritrans.sdk.models.TransactionStatusResponse;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * protected helper class , It contains an static methods which are used to execute the transaction.
 * <p/>
 * Created by shivam on 10/29/15.
 */
class TransactionManager {

    private static Subscription subscription = null;
    private static Subscription cardPaymentSubscription = null;
    private static Subscription paymentStatusSubscription = null;
    private static Subscription cardSubscription = null;
    private static Subscription deleteCardSubscription = null;
    private static Subscription offersSubscription = null;

    public static void registerCard(final Activity activity, CardTokenRequest cardTokenRequest, final
    TransactionCallback callBack) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();
        final String merchantToken = veritransSDK.getMerchantToken(activity);

        if (veritransSDK != null && merchantToken != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getApiClient(activity, true);

            if (apiInterface != null) {

                final Observable<TransactionResponse> observable = apiInterface.registerCard(
                        cardTokenRequest.getCardNumber(),
                        cardTokenRequest.getCardExpiryMonth(),
                        cardTokenRequest.getCardExpiryYear(),
                        cardTokenRequest.getClientKey()
                );

                subscription = observable.subscribeOn(Schedulers
                        .io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<TransactionResponse>() {

                            @Override
                            public void onCompleted() {

                                if (subscription != null && !subscription.isUnsubscribed()) {
                                    subscription.unsubscribe();
                                }

                                releaseResources();
                            }

                            @Override
                            public void onError(Throwable throwable) {

                                Logger.e("error while getting token : ", "" +
                                        throwable.getMessage());
                                callBack.onFailure(throwable.getMessage(), null);
                                releaseResources();
                            }

                            @Override
                            public void onNext(TransactionResponse transactionResponse) {

                                releaseResources();

                                if (transactionResponse != null) {

                                    if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                        displayResponse(transactionResponse);
                                    }

                                    if (transactionResponse.getStatusCode().trim()
                                            .equalsIgnoreCase(Constants.SUCCESS_CODE_200)) {

                                        VeritranceApiInterface apiInterface =
                                                VeritransRestAdapter.getMerchantApiClient(activity, true);

                                        if (apiInterface != null) {
                                            Observable<CardResponse> registerCard = apiInterface
                                                    .registerCard(merchantToken, transactionResponse);

                                            cardSubscription = registerCard.subscribeOn(Schedulers.io())
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe(new Observer<CardResponse>() {
                                                        @Override
                                                        public void onCompleted() {

                                                        }

                                                        @Override
                                                        public void onError(Throwable e) {
                                                            Log.e("CardSubscriber", e.getMessage());
                                                        }

                                                        @Override
                                                        public void onNext(CardResponse cardResponse) {
                                                        }
                                                    });

                                        }
                                        callBack.onSuccess(transactionResponse);
                                    } else {
                                        if (transactionResponse != null && !TextUtils.isEmpty(transactionResponse.getStatusMessage())) {
                                            callBack.onFailure(transactionResponse.getStatusMessage(),
                                                    transactionResponse);
                                        } else {
                                            callBack.onFailure(Constants.ERROR_EMPTY_RESPONSE,
                                                    transactionResponse);
                                        }
                                    }

                                } else {
                                    callBack.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                    Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                }
                            }
                        });
            } else {
                callBack.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            callBack.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            releaseResources();
        }

    }


    /**
     * it will execute an api call to get token from server, and after completion of request it
     * will </p> call appropriate method using registered {@Link TokenCallBack}.
     *
     * @param activity
     * @param cardTokenRequest information about credit card.
     * @param callBack         instance of TokenCallBack to get api status back.
     */
    public static void getToken(Activity activity, CardTokenRequest cardTokenRequest, final
    TokenCallBack callBack) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getApiClient(activity, true);

            if (apiInterface != null) {

                Observable<TokenDetailsResponse> observable;
                if (cardTokenRequest.isTwoClick()) {

                    if (cardTokenRequest.isInstalment()) {
                        observable = apiInterface.getTokenInstalmentOfferTwoClick(
                                cardTokenRequest.getCardCVV(),
                                cardTokenRequest.getSavedTokenId(),
                                cardTokenRequest.isTwoClick(),
                                cardTokenRequest.isSecure(),
                                cardTokenRequest.getGrossAmount(),
                                cardTokenRequest.getBank(),
                                cardTokenRequest.getClientKey(),
                                cardTokenRequest.isInstalment(),
                                cardTokenRequest.getFormattedInstalmentTerm());
                    } else {
                        observable = apiInterface.getTokenTwoClick(
                                cardTokenRequest.getCardCVV(),
                                cardTokenRequest.getSavedTokenId(),
                                cardTokenRequest.isTwoClick(),
                                cardTokenRequest.isSecure(),
                                cardTokenRequest.getGrossAmount(),
                                cardTokenRequest.getBank(),
                                cardTokenRequest.getClientKey());
                    }


                } else {

                    if (cardTokenRequest.isInstalment()) {
                        observable = apiInterface.get3DSTokenInstalmentOffers(cardTokenRequest.getCardNumber(),
                                cardTokenRequest.getCardCVV(),
                                cardTokenRequest.getCardExpiryMonth(), cardTokenRequest
                                        .getCardExpiryYear(),
                                cardTokenRequest.getClientKey(),
                                cardTokenRequest.getBank(),
                                cardTokenRequest.isSecure(),
                                cardTokenRequest.isTwoClick(),
                                cardTokenRequest.getGrossAmount(),
                                cardTokenRequest.isInstalment(),
                                cardTokenRequest.getFormattedInstalmentTerm());
                    } else {
                        observable = apiInterface.get3DSToken(cardTokenRequest.getCardNumber(),
                                cardTokenRequest.getCardCVV(),
                                cardTokenRequest.getCardExpiryMonth(), cardTokenRequest
                                        .getCardExpiryYear(),
                                cardTokenRequest.getClientKey(),
                                cardTokenRequest.getBank(),
                                cardTokenRequest.isSecure(),
                                cardTokenRequest.isTwoClick(),
                                cardTokenRequest.getGrossAmount());
                    }

                }

                subscription = observable.subscribeOn(Schedulers
                        .io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<TokenDetailsResponse>() {

                            @Override
                            public void onCompleted() {

                                if (subscription != null && !subscription.isUnsubscribed()) {
                                    subscription.unsubscribe();
                                }

                                releaseResources();
                            }

                            @Override
                            public void onError(Throwable throwable) {

                                Logger.e("error while getting token : ", "" +
                                        throwable.getMessage());
                                callBack.onFailure(throwable.getMessage(), null);
                                releaseResources();
                            }

                            @Override
                            public void onNext(TokenDetailsResponse tokenDetailsResponse) {

                                releaseResources();

                                if (tokenDetailsResponse != null) {

                                    if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                        displayTokenResponse(tokenDetailsResponse);
                                    }

                                    if (tokenDetailsResponse.getStatusCode().trim()
                                            .equalsIgnoreCase(Constants.SUCCESS_CODE_200)) {
                                        callBack.onSuccess(tokenDetailsResponse);
                                    } else {
                                        if(tokenDetailsResponse!=null && !TextUtils.isEmpty(tokenDetailsResponse.getStatusMessage())){
                                            callBack.onFailure(tokenDetailsResponse.getStatusMessage(),
                                                    tokenDetailsResponse);
                                        }else {
                                            callBack.onFailure(Constants.ERROR_EMPTY_RESPONSE,
                                                    tokenDetailsResponse);
                                        }

                                    }

                                } else {
                                    callBack.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                    Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                }
                            }
                        });

            } else {
                callBack.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            callBack.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            releaseResources();
        }

    }

    /**
     * it will execute an api call to perform transaction using permata bank, and after
     * completion of request it
     * will </p> call appropriate method using registered {@Link TransactionCallback}.
     *
     * @param activity            activity instance
     * @param permataBankTransfer information required perform transaction using permata bank
     * @param callBack            instance of TransactionCallback to get api status back.
     */
    public static void paymentUsingPermataBank(final Activity activity, final PermataBankTransfer
            permataBankTransfer, final TransactionCallback callBack) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {
                Observable<TransactionResponse> observable = null;

                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {
                    observable = apiInterface.paymentUsingPermataBank(merchantToken,
                            permataBankTransfer);

                    subscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (subscription != null && !subscription.isUnsubscribed()) {
                                        subscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callBack.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse
                                                           permataBankTransferResponse) {

                                    releaseResources();

                                    if (permataBankTransferResponse != null) {

                                        if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                            displayResponse(permataBankTransferResponse);
                                        }

                                        if (permataBankTransferResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || permataBankTransferResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {

                                            callBack.onSuccess(permataBankTransferResponse);
                                        } else {
                                            callBack.onFailure(permataBankTransferResponse
                                                            .getStatusMessage(),
                                                    permataBankTransferResponse);
                                            releaseResources();
                                        }

                                    } else {
                                        callBack.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                        releaseResources();
                                    }

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callBack.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callBack.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callBack.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    /**
     * it will execute an api call to perform transaction using credit card, and after
     * completion of request it
     * will </p> call appropriate method using registered {@Link TransactionCallback}.
     *
     * @param activity                       activity instance
     * @param cardTransfer                   information required perform transaction using
     *                                       credit card
     * @param cardPaymentTransactionCallback instance of TransactionCallback to get api status back.
     */
    public static void paymentUsingCard(Activity activity, CardTransfer cardTransfer, final
    TransactionCallback cardPaymentTransactionCallback) {
        VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<TransactionResponse> observable = null;

                //String serverKey = Utils.calculateBase64(veritransSDK.getMerchantToken());
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.paymentUsingCard(merchantToken,
                            cardTransfer);

                    cardPaymentSubscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {
                                @Override
                                public void onCompleted() {

                                    if (cardPaymentSubscription != null &&
                                            !cardPaymentSubscription.isUnsubscribed()) {
                                        cardPaymentSubscription.unsubscribe();
                                    }

                                    releaseResources();
                                }

                                @Override
                                public void onError(Throwable e) {
                                    cardPaymentTransactionCallback.onFailure(e.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse cardPaymentResponse) {

                                    releaseResources();

                                    if (cardPaymentResponse != null) {

                                        if (cardPaymentResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || cardPaymentResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {

                                            cardPaymentTransactionCallback.onSuccess
                                                    (cardPaymentResponse);
                                        } else {
                                            cardPaymentTransactionCallback.onFailure
                                                    (cardPaymentResponse
                                                                    .getStatusMessage(),
                                                            cardPaymentResponse);
                                        }

                                    } else {
                                        cardPaymentTransactionCallback.onFailure(Constants
                                                .ERROR_EMPTY_RESPONSE, null);
                                    }
                                }

                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    releaseResources();
                }
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            releaseResources();
        }
    }

    /**
     * it will execute an api call to perform transaction using mandiri click pay, and after
     * completion of request it
     * will </p> call appropriate method using registered {@Link TransactionCallback}.
     *
     * @param activity                    activity instance
     * @param mandiriClickPayRequestModel information required perform transaction using mandiri
     *                                    click pay.
     * @param callBack                    instance of TransactionCallback to get api status back.
     */
    public static void paymentUsingMandiriClickPay(final Activity activity, final
    MandiriClickPayRequestModel
            mandiriClickPayRequestModel, final TransactionCallback callBack) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<TransactionResponse> observable = null;
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {
                    observable = apiInterface.paymentUsingMandiriClickPay(merchantToken,
                            mandiriClickPayRequestModel);

                    subscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (subscription != null && !subscription.isUnsubscribed()) {
                                        subscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callBack.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse
                                                           mandiriTransferResponse) {

                                    releaseResources();

                                    if (mandiriTransferResponse != null) {

                                        if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                            displayResponse(mandiriTransferResponse);
                                        }

                                        if (mandiriTransferResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || mandiriTransferResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {

                                            callBack.onSuccess(mandiriTransferResponse);
                                        } else {
                                            callBack.onFailure(mandiriTransferResponse
                                                    .getStatusMessage(), mandiriTransferResponse);
                                        }

                                    } else {
                                        callBack.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE, null);
                                    }

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callBack.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callBack.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callBack.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    /**
     * it will execute an api call to perform transaction using mandiri bill pay, and after
     * completion of request it
     * will </p> call appropriate method using registered {@Link TransactionCallback}.
     *
     * @param activity                    activity instance.
     * @param mandiriBillPayTransferModel information required perform transaction using mandiri
     *                                    bill pay.
     * @param callBack                    instance of TransactionCallback to get api status back.
     */
    public static void paymentUsingMandiriBillPay(Activity activity, MandiriBillPayTransferModel
            mandiriBillPayTransferModel, final TransactionCallback callBack) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<TransactionResponse> observable = null;

                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.paymentUsingMandiriBillPay(merchantToken,
                            mandiriBillPayTransferModel);

                    subscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (subscription != null && !subscription.isUnsubscribed()) {
                                        subscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callBack.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse
                                                           permataBankTransferResponse) {

                                    releaseResources();

                                    if (permataBankTransferResponse != null) {

                                        if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                            displayResponse(permataBankTransferResponse);
                                        }

                                        if (permataBankTransferResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || permataBankTransferResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {

                                            callBack.onSuccess(permataBankTransferResponse);
                                        } else {
                                            callBack.onFailure(permataBankTransferResponse
                                                            .getStatusMessage(),
                                                    permataBankTransferResponse);
                                        }

                                    } else {
                                        callBack.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callBack.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callBack.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callBack.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    public static void paymentUsingCIMBPay(Activity activity, CIMBClickPayModel cimbClickPayModel,
                                           final TransactionCallback callback) {
        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();
        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);
            if (apiInterface != null) {
                Observable<TransactionResponse> observable = null;
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.paymentUsingCIMBClickPay(merchantToken,
                            cimbClickPayModel);
                    subscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {
                                @Override
                                public void onCompleted() {
                                    if (subscription != null && !subscription.isUnsubscribed()) {
                                        subscription.unsubscribe();
                                    }
                                    releaseResources();
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse cimbPayTransferResponse) {

                                    releaseResources();

                                    if (cimbPayTransferResponse != null) {
                                        if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                            displayResponse(cimbPayTransferResponse);
                                        }
                                        if (cimbPayTransferResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || cimbPayTransferResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {
                                            callback.onSuccess(cimbPayTransferResponse);
                                        } else {
                                            callback.onFailure(cimbPayTransferResponse
                                                            .getStatusMessage(),
                                                    cimbPayTransferResponse);
                                        }
                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }
                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }
        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    public static void paymentUsingMandiriECash(Activity activity, MandiriECashModel
            mandiriECashModel,
                                                final TransactionCallback callback) {
        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();
        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);
            if (apiInterface != null) {
                Observable<TransactionResponse> observable = null;
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {
                    observable = apiInterface.paymentUsingMandiriECash(merchantToken,
                            mandiriECashModel);
                    subscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {
                                @Override
                                public void onCompleted() {
                                    if (subscription != null && !subscription.isUnsubscribed()) {
                                        subscription.unsubscribe();
                                    }
                                    releaseResources();
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse transferResponse) {

                                    releaseResources();

                                    if (transferResponse != null) {
                                        if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                            displayResponse(transferResponse);
                                        }
                                        if (transferResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || transferResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {
                                            callback.onSuccess(transferResponse);
                                        } else {
                                            callback.onFailure(transferResponse
                                                            .getStatusMessage(),
                                                    transferResponse);
                                        }
                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }
                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }
        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    public static void paymentUsingEpayBri(Activity activity, EpayBriTransfer epayBriTransfer,
                                           final TransactionCallback callback) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<TransactionResponse> observable = null;

                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {
                    observable = apiInterface.paymentUsingEpayBri(merchantToken,
                            epayBriTransfer);

                    subscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (subscription != null && !subscription.isUnsubscribed()) {
                                        subscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse
                                                           epayBriTransferResponse) {

                                    releaseResources();

                                    if (epayBriTransferResponse != null) {

                                        if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                            displayResponse(epayBriTransferResponse);
                                        }

                                        if (epayBriTransferResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || epayBriTransferResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {

                                            callback.onSuccess(epayBriTransferResponse);
                                        } else {
                                            callback.onFailure(epayBriTransferResponse
                                                            .getStatusMessage(),
                                                    epayBriTransferResponse);
                                        }

                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    public static void getPaymentStatus(Activity activity, String id, final PaymentStatusCallback
            callback) {
        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<TransactionStatusResponse> observable = null;

                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {
                    observable = apiInterface.transactionStatus(merchantToken,
                            id);
                    paymentStatusSubscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionStatusResponse>() {

                                @Override
                                public void onCompleted() {
                                    if (paymentStatusSubscription != null &&
                                            !paymentStatusSubscription.isUnsubscribed()) {
                                        paymentStatusSubscription.unsubscribe();
                                    }
                                    releaseResources();
                                }

                                @Override
                                public void onError(Throwable e) {
                                    callback.onFailure(e.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionStatusResponse
                                                           transactionStatusResponse) {

                                    releaseResources();

                                    if (transactionStatusResponse != null) {
                                        if (TextUtils.isEmpty(transactionStatusResponse
                                                .getStatusCode())) {
                                            if (transactionStatusResponse.getStatusCode()
                                                    .equalsIgnoreCase(Constants.SUCCESS_CODE_200) ||
                                                    transactionStatusResponse.getStatusCode()
                                                            .equalsIgnoreCase(Constants
                                                                    .SUCCESS_CODE_201)) {
                                                callback.onSuccess(transactionStatusResponse);
                                            }
                                        } else {
                                            callback.onFailure(Constants.ERROR_EMPTY_RESPONSE,
                                                    transactionStatusResponse);
                                        }
                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                    }
                                }
                            });

                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    public static void paymentUsingIndosatDompetku(final Activity activity, final
    IndosatDompetkuRequest
            indosatDompetkuRequest, final TransactionCallback callback) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<TransactionResponse> observable = null;

                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.paymentUsingIndosatDompetku(merchantToken,
                            indosatDompetkuRequest);

                    subscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (subscription != null && !subscription.isUnsubscribed()) {
                                        subscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse
                                                           permataBankTransferResponse) {

                                    releaseResources();

                                    if (permataBankTransferResponse != null) {

                                        if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                            displayResponse(permataBankTransferResponse);
                                        }

                                        if (permataBankTransferResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || permataBankTransferResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {

                                            callback.onSuccess(permataBankTransferResponse);
                                        } else {
                                            callback.onFailure(permataBankTransferResponse
                                                            .getStatusMessage(),
                                                    permataBankTransferResponse);
                                        }

                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }

                                    releaseResources();

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    public static void paymentUsingIndomaret(final Activity activity, final IndomaretRequestModel
            indomaretRequestModel, final TransactionCallback callback) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<TransactionResponse> observable = null;
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.paymentUsingIndomaret(merchantToken,
                            indomaretRequestModel);

                    subscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (subscription != null && !subscription.isUnsubscribed()) {
                                        subscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse
                                                           indomaretTransferResponse) {

                                    releaseResources();
                                    if (indomaretTransferResponse != null) {

                                        if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                            displayResponse(indomaretTransferResponse);
                                        }

                                        if (indomaretTransferResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || indomaretTransferResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {

                                            callback.onSuccess(indomaretTransferResponse);
                                        } else {
                                            callback.onFailure(indomaretTransferResponse
                                                            .getStatusMessage(),
                                                    indomaretTransferResponse);
                                        }

                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }
                                    releaseResources();
                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }


    public static void paymentUsingBBMMoney(final Activity activity, final BBMMoneyRequestModel
            bbmMoneyRequestModel, final TransactionCallback callback) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<TransactionResponse> observable = null;
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.paymentUsingBBMMoney(merchantToken,
                            bbmMoneyRequestModel);

                    subscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<TransactionResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (subscription != null && !subscription.isUnsubscribed()) {
                                        subscription.unsubscribe();
                                    }
                                    releaseResources();
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(TransactionResponse
                                                           bbmMoneyTransferResponse) {


                                    if (bbmMoneyTransferResponse != null) {

                                        if (veritransSDK != null && veritransSDK.isLogEnabled()) {
                                            displayResponse(bbmMoneyTransferResponse);
                                        }

                                        if (bbmMoneyTransferResponse.getStatusCode().trim()
                                                .equalsIgnoreCase(Constants.SUCCESS_CODE_200)
                                                || bbmMoneyTransferResponse.getStatusCode()
                                                .trim().equalsIgnoreCase(Constants
                                                        .SUCCESS_CODE_201)) {

                                            callback.onSuccess(bbmMoneyTransferResponse);
                                        } else {
                                            callback.onFailure(bbmMoneyTransferResponse
                                                            .getStatusMessage(),
                                                    bbmMoneyTransferResponse);
                                            releaseResources();
                                        }

                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                        releaseResources();
                                    }

                                    releaseResources();

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    public static void saveCards(final Activity activity, final CardTokenRequest cardTokenRequest,
                                 final SavedCardCallback callback) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<CardResponse> observable = null;
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.saveCard(merchantToken,
                            cardTokenRequest);

                    cardSubscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<CardResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (cardSubscription != null && !cardSubscription.isUnsubscribed()) {
                                        cardSubscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(CardResponse cardResponse) {

                                    releaseResources();
                                    if (cardResponse != null) {

                                        if (cardResponse.getMessage().equalsIgnoreCase(activity.getString(R.string.success))) {

                                            callback.onSuccess(cardResponse);
                                        } else {
                                            callback.onFailure(cardResponse.getError(),
                                                    cardResponse);
                                        }

                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    public static void getCards(final Activity activity,
                                final SavedCardCallback callback) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<CardResponse> observable = null;
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.getCard(merchantToken
                    );

                    cardSubscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<CardResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (cardSubscription != null && !cardSubscription.isUnsubscribed()) {
                                        cardSubscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(CardResponse cardResponse) {

                                    releaseResources();
                                    if (cardResponse != null) {

                                        if (cardResponse.getMessage().equalsIgnoreCase(activity.getString(R.string.success))) {

                                            callback.onSuccess(cardResponse);
                                        } else {
                                            callback.onFailure(cardResponse.getError(),
                                                    cardResponse);
                                        }

                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }

    private static void displayTokenResponse(TokenDetailsResponse tokenDetailsResponse) {
        Logger.d("token response: status code ", "" +
                tokenDetailsResponse.getStatusCode());
        Logger.d("token response: status message ", "" +
                tokenDetailsResponse.getStatusMessage());
        Logger.d("token response: token Id ", "" + tokenDetailsResponse
                .getTokenId());
        Logger.d("token response: redirect url ", "" +
                tokenDetailsResponse.getRedirectUrl());
        Logger.d("token response: bank ", "" + tokenDetailsResponse
                .getBank());
    }

    private static void displayResponse(TransactionResponse
                                                transferResponse) {
        Logger.d("transfer response: virtual account" +
                " number ", "" +
                transferResponse.getPermataVANumber());

        Logger.d(" transfer response: status message " +
                "", "" +
                transferResponse.getStatusMessage());

        Logger.d(" transfer response: status code ",
                "" + transferResponse.getStatusCode());

        Logger.d(" transfer response: transaction Id ",
                "" + transferResponse
                        .getTransactionId());

        Logger.d(" transfer response: transaction " +
                        "status ",
                "" + transferResponse
                        .getTransactionStatus());
    }

    private static void releaseResources() {
        if (VeritransSDK.getVeritransSDK() != null) {
            VeritransSDK.getVeritransSDK().isRunning = false;
            Logger.i("released transaction");
        }
    }

    public static void deleteCard(final Activity activity, CardTokenRequest creditCard, final DeleteCardCallback callback) {
        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<DeleteCardResponse> observable = null;
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.deleteCard(merchantToken,
                            creditCard);

                    deleteCardSubscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<DeleteCardResponse>() {

                                @Override
                                public void onCompleted() {

                                    if (deleteCardSubscription != null && !deleteCardSubscription.isUnsubscribed()) {
                                        deleteCardSubscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage());
                                    releaseResources();
                                }

                                @Override
                                public void onNext(DeleteCardResponse deleteCardResponse) {
                                    releaseResources();
                                    if (deleteCardResponse != null) {
                                        if (deleteCardResponse.getMessage().equalsIgnoreCase(
                                                activity.getString(R.string.success))) {
                                            callback.onSuccess(deleteCardResponse);
                                        } else {
                                            callback.onFailure(deleteCardResponse.getError());
                                        }

                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            releaseResources();
        }
    }

    public static void getOffers(final Activity activity,
                                 final GetOffersCallback callback) {

        final VeritransSDK veritransSDK = VeritransSDK.getVeritransSDK();

        if (veritransSDK != null) {
            VeritranceApiInterface apiInterface =
                    VeritransRestAdapter.getMerchantApiClient(activity, true);

            if (apiInterface != null) {

                Observable<GetOffersResponseModel> observable = null;
                String merchantToken = veritransSDK.getMerchantToken(activity);
                Logger.i("merchantToken:" + merchantToken);
                if (merchantToken != null) {

                    observable = apiInterface.getOffers(merchantToken
                    );

                    offersSubscription = observable.subscribeOn(Schedulers
                            .io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<GetOffersResponseModel>() {

                                @Override
                                public void onCompleted() {

                                    if (offersSubscription != null && !offersSubscription.isUnsubscribed()) {
                                        offersSubscription.unsubscribe();
                                    }

                                    releaseResources();

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    callback.onFailure(throwable.getMessage(), null);
                                    releaseResources();
                                }

                                @Override
                                public void onNext(GetOffersResponseModel getOffersResponseModel) {

                                    releaseResources();
                                    if (getOffersResponseModel != null) {

                                        if (getOffersResponseModel.getMessage().equalsIgnoreCase(activity.getString(R.string.success))) {

                                            callback.onSuccess(getOffersResponseModel);
                                        } else {
                                            callback.onFailure(getOffersResponseModel.getMessage(),
                                                    getOffersResponseModel);
                                        }

                                    } else {
                                        callback.onFailure(Constants.ERROR_EMPTY_RESPONSE, null);
                                        Logger.e(Constants.ERROR_EMPTY_RESPONSE);
                                    }

                                }
                            });
                } else {
                    Logger.e(Constants.ERROR_INVALID_DATA_SUPPLIED);
                    callback.onFailure(Constants.ERROR_INVALID_DATA_SUPPLIED, null);
                    releaseResources();
                }
            } else {
                callback.onFailure(Constants.ERROR_UNABLE_TO_CONNECT, null);
                Logger.e(Constants.ERROR_UNABLE_TO_CONNECT);
                releaseResources();
            }

        } else {
            Logger.e(Constants.ERROR_SDK_IS_NOT_INITIALIZED);
            callback.onFailure(Constants.ERROR_SDK_IS_NOT_INITIALIZED, null);
            releaseResources();
        }
    }
}