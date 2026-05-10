package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Metadata about a registered energy price provider.
 */
@Schema(description = "Energy price provider info")
public record ProviderInfoResponse(

  @Schema(description = "Unique provider ID used in configuration", example = "AWATTAR")
  String providerId,

  @Schema(description = "Human-readable display name", example = "aWATTar AT (spot market)")
  String displayName,

  @Schema(description = "Whether this provider supports automatic scheduled price fetch",
    example = "true")
  boolean autoFetchSupported,

  @Schema(description = "Directions this provider supports: IMPORT and/or EXPORT",
    example = "[\"EXPORT\"]")
  List<String> supportedDirections
) {
}
