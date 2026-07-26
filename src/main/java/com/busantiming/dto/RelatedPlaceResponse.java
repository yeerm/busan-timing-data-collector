package com.busantiming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/** TarRlteTarService1 areaBasedList1(지역기반 연관관광지) 응답. */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelatedPlaceResponse {

    private Response response;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private Header header;
        private Body body;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonSetter(nulls = Nulls.SKIP)
        private Object items;
        private int numOfRows;
        private int pageNo;
        private int totalCount;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {
        private List<Item> item;
    }

    @Getter
    @Setter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String baseYm;
        @JsonProperty("tAtsCd")
        private String tAtsCd;
        @JsonProperty("tAtsNm")
        private String tAtsNm;
        private String areaCd;
        private String areaNm;
        private String signguCd;
        private String signguNm;
        private String rlteTatsCd;
        private String rlteTatsNm;
        private String rlteRegnCd;
        private String rlteRegnNm;
        private String rlteSignguCd;
        private String rlteSignguNm;
        private String rlteCtgryLclsNm;
        private String rlteCtgryMclsNm;
        private String rlteCtgrySclsNm;
        private String rlteRank;
    }
}
