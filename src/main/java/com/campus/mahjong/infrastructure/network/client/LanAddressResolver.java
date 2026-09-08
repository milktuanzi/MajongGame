package com.campus.mahjong.infrastructure.network.client;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 选择热点网卡 IPv4；可用 -Dmahjong.hostAddress=... 显式覆盖。 */
final class LanAddressResolver {
    private LanAddressResolver() {}

    static String advertisedAddress() {
        String configured = System.getProperty("mahjong.hostAddress", "").trim();
        if (!configured.isEmpty()) return configured;
        try {
            List<Candidate> candidates = new ArrayList<>();
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                var addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    var address = addresses.nextElement();
                    if (address instanceof Inet4Address ipv4 && ipv4.isSiteLocalAddress()) {
                        candidates.add(new Candidate(ipv4.getHostAddress(), priority(network)));
                    }
                }
            }
            return candidates.stream().min(Comparator.comparingInt(Candidate::priority))
                    .map(Candidate::address).orElse("127.0.0.1");
        } catch (SocketException exception) {
            return "127.0.0.1";
        }
    }

    private static int priority(NetworkInterface network) {
        String name = (network.getName() + " " + network.getDisplayName()).toLowerCase(Locale.ROOT);
        if (name.contains("hotspot") || name.contains("local area connection")) return 0;
        if (name.contains("bridge") || name.startsWith("ap") || name.contains(" ap")) return 1;
        if (name.contains("wi-fi") || name.contains("wlan")) return 2;
        if (name.startsWith("en")) return 3;
        return 4;
    }

    private record Candidate(String address, int priority) {}
}
