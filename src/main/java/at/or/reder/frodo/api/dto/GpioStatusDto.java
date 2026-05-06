package at.or.reder.frodo.api.dto;

import java.util.List;

/**
 * GPIO system status response.
 */
public record GpioStatusDto(
  boolean available,
  boolean isRaspberryPi5,
  String platform,
  String errorMessage,
  List<GpioPairStatusDto> pairs
) {}
