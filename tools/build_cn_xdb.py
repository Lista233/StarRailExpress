#!/usr/bin/env python3
"""
从 ip2region_v4.xdb 提取中国 IP 数据，生成压缩版 xdb 文件。
输出文件: src/main/resources/assets/noellesroles/ip_data.xdb

xdb v4 格式:
  Header (256 bytes)
  Vector Index (512 KiB, 256x256 grid, uint32 LE pointers)
  Data Payload (deduplicated region strings)
  Block Index (14 bytes/entry: startIP:4 + endIP:4 + dataLen:2 + dataPtr:4, LE)
"""
import struct
import os

HEADER_SIZE = 256
VECTOR_INDEX_SIZE = 512 * 1024  # 512 KiB
ENTRY_SIZE = 14  # startIP(4) + endIP(4) + dataLen(2) + dataPtr(4)

def ip_to_str(ip):
    return f"{(ip>>24)&0xFF}.{(ip>>16)&0xFF}.{(ip>>8)&0xFF}.{ip&0xFF}"

def read_xdb(xdb_path):
    """读取原始 xdb 文件，返回所有条目"""
    file_size = os.path.getsize(xdb_path)
    
    with open(xdb_path, 'rb') as f:
        # 从 vector index 获取 block index 起始位置
        f.seek(VECTOR_INDEX_SIZE + HEADER_SIZE - 4)  # 读 vector index 最后一个指针
        # 实际上从第一个指针就能获取 block index 起始
        f.seek(HEADER_SIZE)
        block_index_start = struct.unpack('<I', f.read(4))[0]
        
        num_entries = (file_size - block_index_start) // ENTRY_SIZE
        print(f"  Block index at: {block_index_start}")
        print(f"  Total entries: {num_entries}")
        
        # 读取所有 block index 条目
        entries = []
        f.seek(block_index_start)
        for i in range(num_entries):
            raw = f.read(ENTRY_SIZE)
            if len(raw) < ENTRY_SIZE:
                break
            sip = struct.unpack_from('<I', raw, 0)[0]
            eip = struct.unpack_from('<I', raw, 4)[0]
            dlen = struct.unpack_from('<H', raw, 8)[0]
            dptr = struct.unpack_from('<I', raw, 10)[0]
            entries.append((sip, eip, dlen, dptr))
        
        # 读取所有数据
        print(f"  Reading data payloads...")
        data_cache = {}  # dptr -> data string
        for i, (sip, eip, dlen, dptr) in enumerate(entries):
            if dptr not in data_cache:
                f.seek(dptr)
                data_cache[dptr] = f.read(dlen).decode('utf-8', errors='replace')
    
    return entries, data_cache

def filter_chinese(entries, data_cache):
    """过滤出中国 IP 条目"""
    cn_entries = []
    for sip, eip, dlen, dptr in entries:
        data = data_cache[dptr]
        # 数据格式: "国家|省份|城市|运营商|国家代码"
        # 中国条目的国家代码为 "CN" 或国家为 "中国"
        if data.endswith('|CN') or data.startswith('中国|'):
            cn_entries.append((sip, eip, dlen, dptr))
    
    return cn_entries

def build_xdb(cn_entries, data_cache, output_path):
    """构建压缩版 xdb 文件"""
    # 1. 去重数据字符串，建立映射
    unique_data = {}  # data_string -> offset
    data_section = bytearray()
    
    for sip, eip, dlen, dptr in cn_entries:
        data = data_cache[dptr]
        if data not in unique_data:
            offset = len(data_section)
            unique_data[data] = offset
            data_section.extend(data.encode('utf-8'))
    
    print(f"  Unique data strings: {len(unique_data)}")
    print(f"  Data section size: {len(data_section)} bytes")
    
    # 2. 计算各区域偏移
    data_start = HEADER_SIZE + VECTOR_INDEX_SIZE
    block_index_start = data_start + len(data_section)
    total_size = block_index_start + len(cn_entries) * ENTRY_SIZE
    
    print(f"  Data starts at: {data_start}")
    print(f"  Block index starts at: {block_index_start}")
    print(f"  Total file size: {total_size} bytes ({total_size/1024:.1f} KB)")
    
    # 3. 构建 block index 和更新 data_ptr
    new_entries = []
    for sip, eip, dlen, dptr in cn_entries:
        data = data_cache[dptr]
        new_dptr = data_start + unique_data[data]
        new_entries.append((sip, eip, dlen, new_dptr))
    
    # 4. 构建 vector index (256x256 grid)
    # vector_index[i*256+j] = block index offset for first entry where first_octet==i && second_octet>=j
    vector_index = [0] * (256 * 256)
    
    # 对于每个 (i, j)，找到第一个 entry 使得 entry.sip 的高字节 == i 且 entry.sip 的次高字节 >= j
    # 由于 entries 已排序，我们可以用扫描的方式
    entry_idx = 0
    for i in range(256):
        # 找到第一个 first_octet >= i 的条目
        first_for_i = entry_idx
        for idx in range(entry_idx, len(new_entries)):
            if (new_entries[idx][0] >> 24) & 0xFF >= i:
                first_for_i = idx
                break
        else:
            first_for_i = len(new_entries)
        
        for j in range(256):
            # 找到第一个 first_octet == i && second_octet >= j 的条目
            found = False
            for idx in range(first_for_i, len(new_entries)):
                sip = new_entries[idx][0]
                first_octet = (sip >> 24) & 0xFF
                second_octet = (sip >> 16) & 0xFF
                if first_octet > i:
                    break
                if first_octet == i and second_octet >= j:
                    vector_index[i * 256 + j] = block_index_start + idx * ENTRY_SIZE
                    found = True
                    break
            if not found:
                # 指向最后一个条目的位置（或 block index 末尾）
                vector_index[i * 256 + j] = block_index_start + len(new_entries) * ENTRY_SIZE
    
    # 5. 写入文件
    with open(output_path, 'wb') as f:
        # Header (256 bytes)
        header = bytearray(HEADER_SIZE)
        struct.pack_into('<I', header, 0, 0x00010003)  # version
        struct.pack_into('<I', header, 4, len(cn_entries))  # count
        struct.pack_into('<I', header, 8, HEADER_SIZE)  # vector index ptr
        struct.pack_into('<I', header, 12, block_index_start)  # block index ptr
        f.write(header)
        
        # Vector Index (512 KiB)
        vi_data = bytearray(VECTOR_INDEX_SIZE)
        for i in range(256 * 256):
            struct.pack_into('<I', vi_data, i * 4, vector_index[i])
        f.write(vi_data)
        
        # Data Payload
        f.write(data_section)
        
        # Block Index
        for sip, eip, dlen, dptr in new_entries:
            f.write(struct.pack('<IIH I', sip, eip, dlen, dptr))
    
    return total_size

def main():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    xdb_path = os.path.join(base_dir, 'ip2region_v4.xdb')
    output_path = os.path.join(base_dir, 'src', 'main', 'resources', 'assets', 'noellesroles', 'ip_data.xdb')
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    print(f"Reading original xdb: {xdb_path}")
    entries, data_cache = read_xdb(xdb_path)
    print(f"  Total entries: {len(entries)}")
    print(f"  Unique data strings in original: {len(set(data_cache.values()))}")
    
    print(f"\nFiltering Chinese entries...")
    cn_entries = filter_chinese(entries, data_cache)
    print(f"  Chinese entries: {len(cn_entries)}")
    
    print(f"\nBuilding compact xdb...")
    total_size = build_xdb(cn_entries, data_cache, output_path)
    
    # 验证
    print(f"\nVerifying...")
    with open(output_path, 'rb') as f:
        # 搜索 1.0.8.0 (广州)
        target = (1 << 24) | (0 << 16) | (8 << 8) | 0
        block_idx_start = struct.unpack_from('<I', f.read(16), 12)[0]
        num = (os.path.getsize(output_path) - block_idx_start) // ENTRY_SIZE
        
        lo, hi = 0, num - 1
        while lo <= hi:
            mid = (lo + hi) // 2
            f.seek(block_idx_start + mid * ENTRY_SIZE)
            raw = f.read(ENTRY_SIZE)
            sip = struct.unpack_from('<I', raw, 0)[0]
            eip = struct.unpack_from('<I', raw, 4)[0]
            dlen = struct.unpack_from('<H', raw, 8)[0]
            dptr = struct.unpack_from('<I', raw, 10)[0]
            
            if sip <= target <= eip:
                f.seek(dptr)
                data = f.read(dlen).decode('utf-8')
                print(f"  1.0.8.0 -> {data}")
                break
            elif target < sip:
                hi = mid - 1
            else:
                lo = mid + 1
    
    print(f"\nDone! Output: {output_path}")
    print(f"Size: {total_size} bytes ({total_size/1024:.1f} KB)")

if __name__ == '__main__':
    main()
