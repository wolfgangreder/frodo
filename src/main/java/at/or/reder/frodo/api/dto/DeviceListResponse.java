package at.or.reder.frodo.api.dto;

import java.util.List;

/**
 * Response DTO for device list endpoint.
 *
 * @param devices list of device summaries
 * @param total   total number of devices
 */
public record DeviceListResponse(List<DeviceSummary> devices, int total) {
}
