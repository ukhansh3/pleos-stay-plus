using System.Text.Json.Nodes;

namespace StayPlus.Desktop;

internal static class PairingApproval
{
    // WPF IsEnabled is also false while an ancestor is busy. Authorization
    // preconditions must come from the server response, never that UI property.
    internal static bool CanApprove(JsonNode? pair)
    {
        if (pair?["state"]?.GetValue<string>() != "CLAIMED" ||
            pair["approvedDeviceIds"] is not JsonArray approvals) return false;
        var peers = new[] { pair["issuer"], pair["claimant"] };
        var pc = peers.FirstOrDefault(p => p?["kind"]?.GetValue<string>() == "WINDOWS");
        var vehicle = peers.FirstOrDefault(p => p?["kind"]?.GetValue<string>() == "VEHICLE");
        var pcId = pc?["id"]?.GetValue<string>();
        return !string.IsNullOrWhiteSpace(pcId) &&
            !string.IsNullOrWhiteSpace(vehicle?["id"]?.GetValue<string>()) &&
            !string.IsNullOrWhiteSpace(vehicle?["name"]?.GetValue<string>()) &&
            !approvals.Any(value => value?.GetValue<string>() == pcId);
    }
}
