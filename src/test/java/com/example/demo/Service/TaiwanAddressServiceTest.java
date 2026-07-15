package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaiwanAddressServiceTest {

    private final TaiwanAddressService service = new TaiwanAddressService();

    @Test
    void exposesValidCitiesAndDistricts() {
        String city = service.cities().iterator().next();
        String district = service.districts(city).iterator().next();

        assertThat(service.isValidCity(city)).isTrue();
        assertThat(service.isValidCity("  " + city + "  ")).isTrue();
        assertThat(service.isValidDistrict(city, district)).isTrue();
    }

    @Test
    void rejectsUnknownOrBlankAddressValues() {
        assertThat(service.isValidCity(null)).isFalse();
        assertThat(service.isValidCity("不存在縣市")).isFalse();
        assertThat(service.isValidDistrict("不存在縣市", "不存在區域")).isFalse();
        assertThat(service.districts("不存在縣市")).isEmpty();
    }
}
