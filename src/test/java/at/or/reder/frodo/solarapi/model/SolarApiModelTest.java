package at.or.reder.frodo.solarapi.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Solar API model JSON deserialization.
 *
 * <p>Verifies that real-world Solar API JSON responses correctly
 * deserialize into the model objects.</p>
 */
class SolarApiModelTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void testDeserializePowerFlowRealtimeData() throws Exception {
    String json = """
      {
        "Body": {
          "Data": {
            "Inverters": {
              "1": {
                "Battery_Mode": "nearly depleted",
                "DT": 1,
                "E_Total": 352448.24611111113,
                "P": 300.64181518554688,
                "SOC": 8.6
              }
            },
            "Site": {
              "BackupMode": false,
              "BatteryStandby": false,
              "Meter_Location": "grid",
              "Mode": "bidirectional",
              "P_Akku": 3.34,
              "P_Grid": 421.5,
              "P_Load": -719.48,
              "P_PV": 326.41,
              "rel_Autonomy": 41.42,
              "rel_SelfConsumption": 100.0
            },
            "Smartloads": {
              "Ohmpilots": {
                "0": {
                  "P_AC_Total": 0.0,
                  "State": "normal",
                  "Temperature": 52.9
                }
              },
              "OhmpilotEcos": {}
            },
            "SecondaryMeters": {},
            "Version": "13"
          }
        },
        "Head": {
          "RequestArguments": {},
          "Status": {
            "Code": 0,
            "Reason": "",
            "UserMessage": ""
          },
          "Timestamp": "2026-04-10T14:30:00+01:00"
        }
      }
      """;

    var response = (SolarApiResponse<PowerFlowRealtimeData>) mapper.readValue(json,
      mapper.getTypeFactory().constructParametricType(
        SolarApiResponse.class, PowerFlowRealtimeData.class));

    assertNotNull(response);
    assertTrue(response.isSuccess());

    PowerFlowRealtimeData data = response.getData();
    assertNotNull(data);

    // Verify inverter data
    assertEquals(1, data.getInverters().size());
    var inverter = data.getInverters().get("1");
    assertNotNull(inverter);
    assertEquals("nearly depleted", inverter.getBatteryMode());
    assertEquals(300.64181518554688, inverter.getPowerWatts(), 0.01);
    assertEquals(352448.24611111113, inverter.getEnergyWattHours(), 0.01);
    assertEquals(8.6, inverter.getBatterySOC(), 0.01);

    // Verify site data
    var site = data.getSite();
    assertNotNull(site);
    assertEquals(421.5, site.getGridPowerWatts(), 0.01);
    assertEquals(-719.48, site.getLoadPowerWatts(), 0.01);
    assertEquals(326.41, site.getPVPowerWatts(), 0.01);
    assertEquals(3.34, site.getBatteryPowerWatts(), 0.01);
    assertEquals(41.42, site.getAutonomyPercent(), 0.01);
    assertEquals(100.0, site.getSelfConsumptionPercent(), 0.01);

    // Verify Ohmpilot data
    var smartloads = data.getSmartloads();
    assertNotNull(smartloads);
    assertTrue(smartloads.hasDevices());
    assertEquals(1, smartloads.getTotalDeviceCount());

    var ohmpilot = smartloads.getOhmpilots().get("0");
    assertNotNull(ohmpilot);
    assertEquals(0.0, ohmpilot.getPowerWatts(), 0.01);
    assertEquals("normal", ohmpilot.getState());
    assertTrue(ohmpilot.isNormal());
    assertFalse(ohmpilot.isFault());
    assertFalse(ohmpilot.isActive());
    assertEquals(52.9, ohmpilot.getTemperatureCelsius(), 0.01);

    // Verify version
    assertEquals("13", data.getVersion());
    assertTrue(data.hasOhmpilots());
  }

  @Test
  void testOhmpilotDataActiveState() {
    var activeOhmpilot = new OhmpilotData(2500.0, "boost", 65.0);
    assertTrue(activeOhmpilot.isActive());
    assertEquals(2500.0, activeOhmpilot.getPowerWatts());
    assertEquals("boost", activeOhmpilot.getState());
    assertEquals(65.0, activeOhmpilot.getTemperatureCelsius());

    var inactiveOhmpilot = new OhmpilotData(0.0, "standby", 45.0);
    assertFalse(inactiveOhmpilot.isActive());
  }

  @Test
  void testOhmpilotDataFaultState() {
    var faultOhmpilot = new OhmpilotData(0.0, "fault", 70.0);
    assertTrue(faultOhmpilot.isFault());
    assertFalse(faultOhmpilot.isNormal());
    assertFalse(faultOhmpilot.isActive());
  }

  @Test
  void testSmartloadsDataEmpty() {
    var emptySmartloads = new SmartloadsData(null, null);
    assertFalse(emptySmartloads.hasDevices());
    assertEquals(0, emptySmartloads.getTotalDeviceCount());
    assertTrue(emptySmartloads.getOhmpilots().isEmpty());
    assertTrue(emptySmartloads.getOhmpilotEcos().isEmpty());
  }

  @Test
  void testSolarApiResponseFailure() throws Exception {
    String json = """
      {
        "Body": {
          "Data": null
        },
        "Head": {
          "RequestArguments": {},
          "Status": {
            "Code": 1,
            "Reason": "Error",
            "UserMessage": "API call failed"
          },
          "Timestamp": "2026-04-10T14:30:00+01:00"
        }
      }
      """;

    var response = (SolarApiResponse<PowerFlowRealtimeData>) mapper.readValue(json,
      mapper.getTypeFactory().constructParametricType(
        SolarApiResponse.class, PowerFlowRealtimeData.class));

    assertNotNull(response);
    assertFalse(response.isSuccess());
    assertNull(response.getData());
  }
}
