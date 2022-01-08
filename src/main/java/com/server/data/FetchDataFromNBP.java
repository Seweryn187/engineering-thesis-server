package com.server.data;

import com.server.alerts.SendAlerts;
import com.server.entities.Currency;
import com.server.entities.CurrentValue;
import com.server.entities.HistoricalValue;

import com.server.utility.Utility;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import com.server.repositories.CurrencyRepository;
import com.server.repositories.CurrentValueRepository;
import com.server.repositories.HistoricalValueRepository;
import com.server.response.NbpResponse;

import java.time.Duration;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static com.server.utility.Utility.calculateSpread;
import static com.server.utility.Utility.castFloatToInt;

@EnableScheduling
@Service
public class FetchDataFromNBP {

    private final CurrencyRepository currencyRepository;
    private final CurrentValueRepository currentValueRepository;
    private final HistoricalValueRepository historicalValueRepository;
    private final SendAlerts sendAlerts;
    private WebClient webClient;

    private static final Logger log = LoggerFactory.getLogger(FetchDataFromNBP.class);

    @Autowired
    public FetchDataFromNBP(CurrencyRepository currencyRepository, CurrentValueRepository currentValueRepository,
                            HistoricalValueRepository historicalValueRepository, SendAlerts sendAlerts) {
        this.currencyRepository = currencyRepository;
        this.currentValueRepository = currentValueRepository;
        this.historicalValueRepository = historicalValueRepository;
        this.sendAlerts = sendAlerts;
    }


    /* (cron = "0 0 2 * * 0-5") - cron expression, which dictates at what time this function have to
    start, in this case every day in working week at 2:00 am
    */
    @Transactional
    @Scheduled(fixedDelay = 30000)
    public void saveNewCurrentValue() {
        CurrentValue newCurrentValue = new CurrentValue();
        int triesCount = 0;
        int maxTries = 3;
        int recordCount = 0;
        long recordAmount;
        LocalDate localDate = LocalDate.now();

        buildClient();
        recordAmount = this.currencyRepository.count();
        while(triesCount < maxTries) {
            try {
                log.info("-------------Getting new value and archiving old one (Sync)----------------");

                for (CurrentValue record : this.currentValueRepository.findCurrentValueBySourceName("The National Bank of Poland")) {
                    NbpResponse response = getCurrentValueFromNBP(record);
                    if (response != null) {
                        if(response.getRates().get(0).getEffectiveDate().getDayOfMonth() != record.getDate().getDayOfMonth()){
                            archiveCurrentValue(record);
                        }

                        newCurrentValue.setId(record.getId());
                        newCurrentValue.setBidValue(castFloatToInt(response.getRates().get(0).getBid())); // Because in database I'm saving integer not a float value
                        newCurrentValue.setAskValue(castFloatToInt(response.getRates().get(0).getAsk()));
                        newCurrentValue.setMeanValue(castFloatToInt((response.getRates().get(0).getBid() + response.getRates().get(0).getAsk())/2));
                        newCurrentValue.setSource(record.getSource());
                        if(response.getRates().get(0).getEffectiveDate().getDayOfMonth() != localDate.getDayOfMonth()){
                            newCurrentValue.setDate(localDate);
                        } else{
                            newCurrentValue.setDate(response.getRates().get(0).getEffectiveDate());
                        }
                        newCurrentValue.setSpread(calculateSpread(newCurrentValue.getAskValue(), newCurrentValue.getBidValue(), newCurrentValue.getMeanValue()));
                        newCurrentValue.setAskIncrease(record.getAskValue() < response.getRates().get(0).getAsk());
                        newCurrentValue.setBidIncrease(record.getBidValue() < response.getRates().get(0).getBid());
                        newCurrentValue.setCurrency(record.getCurrency());

                        CurrentValue addedValue = this.currentValueRepository.save(newCurrentValue);
                        sendAlerts.sendValueChangeAlerts(record, newCurrentValue);
                        log.info("I've fetched new value: " + addedValue);

                        if(addedValue != null){
                            ++recordCount;
                        }
                    }
                }
                log.info("-------------------End--------------------");
                if(recordCount == recordAmount){
                    break;
                }
            } catch (Exception ex) {
                log.error("Error getting current value from NBP " + ex.getMessage());
                if(++triesCount == maxTries) throw ex;
            }

        }


    }

    public void buildClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofMillis(5000))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS)));

        webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://api.nbp.pl/api/exchangerates/rates/")
                .build();
    }

    public NbpResponse getCurrentValueFromNBP(CurrentValue record) {
        String table = "c/"; // it comes from the structure of api request of NBP, this table has bought and sell value
        String format = "?format=json";
        return webClient.get()
                .uri(table + record.getCurrency().getAbbr() + "/" + format)
                .retrieve()
                .bodyToMono(NbpResponse.class)
                .block();
    }

    @Transactional
    public void archiveCurrentValue(CurrentValue record) {
        int count = 0;
        int maxTries = 3;

        while(count < maxTries) {
            try {
                if (record != null) {
                    HistoricalValue oldValue = new HistoricalValue();
                    oldValue.setMeanValue(record.getMeanValue());
                    oldValue.setMeanBidValue(record.getBidValue());
                    oldValue.setMeanAskValue(record.getAskValue());
                    oldValue.setSource(record.getSource());
                    oldValue.setCurrency(record.getCurrency());
                    oldValue.setDate(record.getDate());
                    oldValue.setSpread(record.getSpread());

                    HistoricalValue archivedRecord = this.historicalValueRepository.save(oldValue);
                    log.info("I've archived value: " + archivedRecord);
                    if(archivedRecord != null){
                        break;
                    }
                }
            } catch (Exception ex) {
                log.error("Error archiving value in database: " + ex.getMessage());
                if(++count == maxTries) throw ex;
            }
        }
    }


}