package com.example.demo.dto.request;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "攤主活動報名送出請求")
public class VendorApplicationSubmitRequest {

  @NotNull(message = "請提供活動 ID")
  @Schema(description = "活動 ID", example = "1")
  private Long eventId;

  @NotEmpty(message = "請提供報名活動日期")
  @Schema(description = "報名活動日期，日期必須落在活動期間內", example = "[\"2026-08-01\", \"2026-08-02\"]")
  private List<@NotNull(message = "請提供報名活動日期") LocalDate> applyDates;

  @Schema(description = "車牌號碼", example = "ABC-1234")
  private String vehicleNo;

  @Schema(description = "攤主報名備註", example = "需要靠近出入口的位置")
  private String applicantNote;

  @Valid
  @Schema(description = "租借設備與加購用電資料")
  private List<EquipmentRental> equipmentRentals;

  public Long getEventId() {
    return eventId;
  }

  public void setEventId(Long eventId) {
    this.eventId = eventId;
  }

  public List<LocalDate> getApplyDates() {
    return applyDates;
  }

  public void setApplyDates(List<LocalDate> applyDates) {
    this.applyDates = applyDates;
  }

  public String getVehicleNo() {
    return vehicleNo;
  }

  public void setVehicleNo(String vehicleNo) {
    this.vehicleNo = vehicleNo;
  }

  public String getApplicantNote() {
    return applicantNote;
  }

  public void setApplicantNote(String applicantNote) {
    this.applicantNote = applicantNote;
  }

  public List<EquipmentRental> getEquipmentRentals() {
    return equipmentRentals;
  }

  public void setEquipmentRentals(List<EquipmentRental> equipmentRentals) {
    this.equipmentRentals = equipmentRentals;
  }

  @Schema(description = "單筆租借設備或用電申請")
  public static class EquipmentRental {

    @NotNull(message = "請提供活動設備 ID")
    @Schema(description = "活動設備 ID", example = "3")
    private Long eventEquipmentId;

    @Min(value = 1, message = "設備租借數量必須大於 0")
    @Schema(description = "租借數量；未填時預設 1", example = "1")
    private Integer quantity;

    @Min(value = 1, message = "租借單位數必須大於 0")
    @Schema(description = "租借單位數；未填時預設報名日期數", example = "2")
    private Integer rentalUnits;

    @Valid
    @Schema(description = "加購用電時填寫的電器清單")
    private List<Appliance> appliances;

    public Long getEventEquipmentId() {
      return eventEquipmentId;
    }

    public void setEventEquipmentId(Long eventEquipmentId) {
      this.eventEquipmentId = eventEquipmentId;
    }

    public Integer getQuantity() {
      return quantity;
    }

    public void setQuantity(Integer quantity) {
      this.quantity = quantity;
    }

    public Integer getRentalUnits() {
      return rentalUnits;
    }

    public void setRentalUnits(Integer rentalUnits) {
      this.rentalUnits = rentalUnits;
    }

    public List<Appliance> getAppliances() {
      return appliances;
    }

    public void setAppliances(List<Appliance> appliances) {
      this.appliances = appliances;
    }
  }

  @Schema(description = "用電電器資料")
  public static class Appliance {

    @NotBlank(message = "請提供電器名稱")
    @Schema(description = "電器名稱", example = "咖啡機")
    private String applianceName;

    @NotNull(message = "請提供電器瓦數")
    @Min(value = 1, message = "電器瓦數必須大於 0")
    @Schema(description = "電器瓦數", example = "800")
    private Integer wattage;

    public String getApplianceName() {
      return applianceName;
    }

    public void setApplianceName(String applianceName) {
      this.applianceName = applianceName;
    }

    public Integer getWattage() {
      return wattage;
    }

    public void setWattage(Integer wattage) {
      this.wattage = wattage;
    }
  }
}
