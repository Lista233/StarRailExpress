package org.agmas.noellesroles.role.qust.util;

import org.agmas.noellesroles.Noellesroles;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * IP 属地查询工具类（xdb 二进制格式）。
 * <p>
 * 直接读取 ip2region xdb v4 二进制文件，仅包含中国省市数据。
 * 非中国 IP 返回 "外国"。
 * <p>
 * xdb 格式：
 * <ul>
 *   <li>Header (256 bytes): version, count, vectorIndexPtr, blockIndexPtr</li>
 *   <li>Vector Index (512 KiB): 256×256 网格，快速定位</li>
 *   <li>Data Payload: 去重的地区字符串</li>
 *   <li>Block Index (14 bytes/条目): startIP(4) + endIP(4) + dataLen(2) + dataPtr(4)</li>
 * </ul>
 */
public class IPLocator {

    private static final String XDB_PATH = "assets/noellesroles/ip_data.xdb";
    private static final int HEADER_SIZE = 256;
    private static final int VECTOR_INDEX_SIZE = 512 * 1024; // 512 KiB
    private static final int ENTRY_SIZE = 14; // startIP(4) + endIP(4) + dataLen(2) + dataPtr(4)

    private static byte[] xdbData = null;
    private static int blockIndexStart = 0;
    private static int numEntries = 0;
    private static boolean loaded = false;

    /**
     * 加载 xdb 数据到内存
     */
    public static synchronized void load() {
        if (loaded) return;

        try {
            InputStream is = IPLocator.class.getClassLoader().getResourceAsStream(XDB_PATH);
            if (is == null) {
                Noellesroles.LOGGER.warn("IP xdb file not found: {}", XDB_PATH);
                loaded = true;
                return;
            }

            // 读取整个文件到内存
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            is.close();
            xdbData = baos.toByteArray();

            // 解析 header
            ByteBuffer header = ByteBuffer.wrap(xdbData, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            // int version = header.getInt(0); // 0x00010003
            // int count = header.getInt(4);
            // int vectorIndexPtr = header.getInt(8);
            blockIndexStart = header.getInt(12);

            numEntries = (xdbData.length - blockIndexStart) / ENTRY_SIZE;
            Noellesroles.LOGGER.info("Loaded IP xdb: {} entries, {} KB",
                    numEntries, xdbData.length / 1024);

            loaded = true;
        } catch (Exception e) {
            Noellesroles.LOGGER.error("Failed to load IP xdb", e);
        }
    }

    /**
     * 查询 IP 属地
     *
     * @param ip IP 地址字符串（IPv4）
     * @return 属地信息（如 "广东省 广州市"），非中国 IP 返回 "外国"，IP 为空返回 "未知"
     */
    public static String locate(String ip) {
        if (!loaded) {
            load();
        }

        // IP 为空时返回 "未知"
        if (ip == null || ip.isEmpty() || ip.equals("unknown")) {
            return "未知";
        }

        // xdb 未加载时返回 "未知"
        if (xdbData == null) {
            return "未知";
        }

        // 仅支持 IPv4
        if (ip.contains(":")) {
            return "外国";
        }

        try {
            long ipNum = ipv4ToLong(ip);
            return search(ipNum);
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 二分查找 IP 对应的地区
     */
    private static String search(long ipNum) {
        ByteBuffer buf = ByteBuffer.wrap(xdbData).order(ByteOrder.LITTLE_ENDIAN);

        // 使用 vector index 加速：根据 IP 第一字节定位搜索起点
        int ipHigh = (int) ((ipNum >> 24) & 0xFF);
        int vectorOffset = HEADER_SIZE + ipHigh * 256 * 4; // vector index 行起始
        // 实际可以进一步用第二字节优化，但二分搜索已经够快

        // 二分搜索 block index
        int lo = 0, hi = numEntries - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int entryOffset = blockIndexStart + mid * ENTRY_SIZE;

            long startIP = buf.getInt(entryOffset) & 0xFFFFFFFFL;
            long endIP = buf.getInt(entryOffset + 4) & 0xFFFFFFFFL;

            if (ipNum >= startIP && ipNum <= endIP) {
                // 找到了
                int dataLen = buf.getShort(entryOffset + 8) & 0xFFFF;
                int dataPtr = buf.getInt(entryOffset + 10);

                // 读取数据
                byte[] dataBytes = new byte[dataLen];
                System.arraycopy(xdbData, dataPtr, dataBytes, 0, dataLen);
                String region = new String(dataBytes, java.nio.charset.StandardCharsets.UTF_8);

                return parseRegion(region);
            } else if (ipNum < startIP) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }

        return "外国";
    }

    /**
     * 解析地区字符串。
     * 格式: "国家|省份|城市|运营商|国家代码"
     * 返回: "省份 城市" 或 "省份"（直辖市）
     */
    private static String parseRegion(String region) {
        if (region == null || region.isEmpty()) {
            return "外国";
        }

        String[] parts = region.split("\\|");
        if (parts.length < 3) {
            return "外国";
        }

        String country = parts[0];
        String province = parts[1];
        String city = parts[2];

        // 非中国数据
        if (!country.equals("中国")) {
            return "外国";
        }

        // 清理 "0" 占位符
        if (province.equals("0") || province.isEmpty()) {
            return "外国";
        }

        // 直辖市只显示一次
        if (province.equals(city) || city.equals("0") || city.isEmpty()) {
            return province;
        }

        return province + " " + city;
    }

    /**
     * 将 IPv4 地址转换为 long
     */
    private static long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return 0;
        }
        return ((long) Integer.parseInt(parts[0]) << 24)
                | ((long) Integer.parseInt(parts[1]) << 16)
                | ((long) Integer.parseInt(parts[2]) << 8)
                | Integer.parseInt(parts[3]);
    }

    /**
     * 从 InetAddress 查询 IP 属地
     */
    public static String locate(InetAddress address) {
        if (address == null) return "未知";
        return locate(address.getHostAddress());
    }
}
