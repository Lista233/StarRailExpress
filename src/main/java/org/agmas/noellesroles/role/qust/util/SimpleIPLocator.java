package org.agmas.noellesroles.role.qust.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 简化的IP属地查询工具（模拟版本）。
 * <p>
 * 为避免引入外部依赖和大型数据库文件，这里使用简化的模拟实现：
 * - 根据IP前缀返回预设的省市运营商信息
 * - 生产环境可替换为真实的ip2region实现
 * <p>
 * 使用方式：
 * <pre>{@code
 * IPInfo info = SimpleIPLocator.locate("192.168.1.1");
 * String display = info.getCity() + " · " + info.getIsp();
 * }</pre>
 */
public class SimpleIPLocator {

    private static final Map<String, IPInfo> IP_PREFIX_MAP = new HashMap<>();

    static {
        // 模拟常见IP段（实际项目中应使用真实IP数据库）
        // 格式：前两段.前三段 -> 省市运营商

        // 广东省
        IP_PREFIX_MAP.put("default", new IPInfo("广东省", "深圳市", "中国电信"));
        IP_PREFIX_MAP.put("192.168", new IPInfo("广东省", "深圳市", "中国电信"));
        IP_PREFIX_MAP.put("10.0", new IPInfo("北京市", "北京市", "中国联通"));
        IP_PREFIX_MAP.put("172.16", new IPInfo("上海市", "上海市", "中国移动"));

        // 一些示例城市
        IP_PREFIX_MAP.put("113.108", new IPInfo("广东省", "广州市", "中国电信"));
        IP_PREFIX_MAP.put("119.147", new IPInfo("广东省", "深圳市", "中国移动"));
        IP_PREFIX_MAP.put("61.144", new IPInfo("广东省", "深圳市", "中国联通"));
        IP_PREFIX_MAP.put("123.125", new IPInfo("北京市", "北京市", "中国联通"));
        IP_PREFIX_MAP.put("220.181", new IPInfo("北京市", "北京市", "中国电信"));
        IP_PREFIX_MAP.put("101.226", new IPInfo("上海市", "上海市", "中国移动"));
        IP_PREFIX_MAP.put("140.207", new IPInfo("浙江省", "杭州市", "中国电信"));
        IP_PREFIX_MAP.put("183.232", new IPInfo("四川省", "成都市", "中国移动"));
        IP_PREFIX_MAP.put("110.52", new IPInfo("江苏省", "南京市", "中国电信"));
    }

    /**
     * 根据IP地址查询归属地信息
     *
     * @param ip IP地址字符串（如 "192.168.1.1"）
     * @return IP归属地信息
     */
    public static IPInfo locate(String ip) {
        if (ip == null || ip.isEmpty()) {
            return IP_PREFIX_MAP.get("default");
        }

        // 提取前两段和前三段
        String[] parts = ip.split("\\.");
        if (parts.length < 2) {
            return IP_PREFIX_MAP.get("default");
        }

        // 先尝试匹配前三段
        if (parts.length >= 3) {
            String prefix3 = parts[0] + "." + parts[1] + "." + parts[2];
            IPInfo info = IP_PREFIX_MAP.get(prefix3);
            if (info != null) {
                return info;
            }
        }

        // 再尝试匹配前两段
        String prefix2 = parts[0] + "." + parts[1];
        IPInfo info = IP_PREFIX_MAP.get(prefix2);
        if (info != null) {
            return info;
        }

        // 默认返回
        return IP_PREFIX_MAP.get("default");
    }

    /**
     * IP归属地信息
     */
    public static class IPInfo {
        private final String province;  // 省份
        private final String city;      // 城市
        private final String isp;       // 运营商

        public IPInfo(String province, String city, String isp) {
            this.province = province;
            this.city = city;
            this.isp = isp;
        }

        public String getProvince() {
            return province;
        }

        public String getCity() {
            return city;
        }

        public String getIsp() {
            return isp;
        }

        /**
         * 获取完整的属地显示（城市 · 运营商）
         */
        public String getFullLocation() {
            return city + " · " + isp;
        }

        /**
         * 获取省市显示
         */
        public String getProvinceCity() {
            if (province.equals(city)) {
                return city;  // 直辖市只显示一次
            }
            return province + " · " + city;
        }
    }
}
