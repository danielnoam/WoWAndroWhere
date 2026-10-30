"""Small source changes AzerothCore needs to build against Android's libc and MariaDB's client library.

Run on the checked-out source before the Termux build. Each change must apply exactly once, so a newer
AzerothCore that moved the code fails loudly instead of building something different.
"""
import sys
from pathlib import Path

root = Path(sys.argv[1])

SSL_BLOCK = """    if (m_connectionInfo.ssl != "")
    {
        mysql_ssl_mode opt_use_ssl = SSL_MODE_DISABLED;
        if (m_connectionInfo.ssl == "ssl")
        {
            opt_use_ssl = SSL_MODE_REQUIRED;
        }

        mysql_options(mysqlInit, MYSQL_OPT_SSL_MODE, (char const*)&opt_use_ssl);
    }
"""

SSL_BLOCK_MARIADB = """#if defined(LIBMARIADB)
    if (m_connectionInfo.ssl == "ssl")
    {
        my_bool enforce = 1;
        mysql_options(mysqlInit, MYSQL_OPT_SSL_ENFORCE, &enforce);
    }
#else
    if (m_connectionInfo.ssl != "")
    {
        mysql_ssl_mode opt_use_ssl = SSL_MODE_DISABLED;
        if (m_connectionInfo.ssl == "ssl")
        {
            opt_use_ssl = SSL_MODE_REQUIRED;
        }

        mysql_options(mysqlInit, MYSQL_OPT_SSL_MODE, (char const*)&opt_use_ssl);
    }
#endif
"""

PATCHES = [
    # glibc names the cpu_set_t word type __cpu_mask; bionic doesn't.
    ("src/common/Threading/ProcessPriority.cpp",
     "*(__cpu_mask*)(&mask)",
     "*(unsigned long*)(&mask)"),
    # MariaDB's client library reports a version above MySQL 8.3 but has neither the named bind call
    # nor MYSQL_OPT_SSL_MODE.
    ("src/server/database/Database/MySQLConnection.cpp",
     "#if MYSQL_VERSION_ID >= 80300\n    if (mysql_stmt_bind_named_param",
     "#if MYSQL_VERSION_ID >= 80300 && !defined(LIBMARIADB)\n    if (mysql_stmt_bind_named_param"),
    ("src/server/database/Database/MySQLConnection.cpp",
     SSL_BLOCK,
     SSL_BLOCK_MARIADB),
    # MariaDB's client library has its own version (3.x), separate from the server version in MYSQL_VERSION_ID.
    ("src/server/database/Database/DatabaseWorkerPool.cpp",
     "    bool isSupportClientDB = mysql_get_client_version() >= MIN_MYSQL_CLIENT_VERSION;\n"
     "    bool isSameClientDB = mysql_get_client_version() == MYSQL_VERSION_ID;\n",
     "#if defined(LIBMARIADB)\n"
     "    bool isSupportClientDB = true;\n"
     "    bool isSameClientDB = mysql_get_client_version() == MARIADB_PACKAGE_VERSION_ID;\n"
     "#else\n"
     "    bool isSupportClientDB = mysql_get_client_version() >= MIN_MYSQL_CLIENT_VERSION;\n"
     "    bool isSameClientDB = mysql_get_client_version() == MYSQL_VERSION_ID;\n"
     "#endif\n"),
    # The server version check reads one character per version part, so MariaDB 13.x compares as "1" < "8".
    ("src/server/database/Database/DatabaseWorkerPool.cpp",
     "bool DatabaseIncompatibleVersion(std::string const mysqlVersion)\n{\n",
     "bool DatabaseIncompatibleVersion(std::string const mysqlVersion)\n{\n"
     "#if defined(LIBMARIADB)\n"
     "    return false;\n"
     "#endif\n"),
    # With _GNU_SOURCE, bionic's strerror_r is the GNU one that returns char*.
    ("deps/gsoap/stdsoap2.cpp",
     "(_POSIX_C_SOURCE >= 200112L || _XOPEN_SOURCE >= 600)) || defined(__ANDROID__) || !defined(__GLIBC__))\n    err = strerror_r",
     "(_POSIX_C_SOURCE >= 200112L || _XOPEN_SOURCE >= 600)))\n    err = strerror_r"),
    # libc++'s milliseconds count is long long, which matches no ByteBuffer overload where int64_t is long.
    ("src/server/game/Entities/Unit/Unit.cpp",
     "data << GameTime::GetGameTimeMS().count();",
     "data << int64(GameTime::GetGameTimeMS().count());"),
    # The same for prepared statements: chrono counts arrive as long long, which has no instantiation.
    ("src/server/database/Database/PreparedStatement.cpp",
     "template void PreparedStatementBase::SetValidData(const uint8 index, int64 const& value);\n",
     "template void PreparedStatementBase::SetValidData(const uint8 index, int64 const& value);\n"
     "#if defined(__ANDROID__)\n"
     "template<>\n"
     "Acore::Types::is_non_string_view_v<long long> PreparedStatementBase::SetValidData(const uint8 index, long long const& value)\n"
     "{\n"
     "    SetValidData(index, static_cast<int64>(value));\n"
     "}\n"
     "#endif\n"),
    # Android's getaddrinfo can refuse even a numeric address from a process outside the app's own runtime,
    # so a literal IP such as the realm's 127.0.0.1 is parsed directly and only names go to the resolver.
    ("src/common/Asio/Resolver.h",
     "            boost::system::error_code ec;\n"
     "            boost::asio::ip::resolver_base::flags flagsResolver",
     "            boost::system::error_code ec;\n"
     "            boost::asio::ip::address literal = boost::asio::ip::make_address(host, ec);\n"
     "            if (!ec)\n"
     "                return boost::asio::ip::tcp::endpoint(literal, service.empty() ? 0 : static_cast<unsigned short>(std::stoul(service)));\n"
     "            ec.clear();\n"
     "            boost::asio::ip::resolver_base::flags flagsResolver"),
]

for rel, old, new in PATCHES:
    path = root / rel
    text = path.read_text()
    count = text.count(old)
    expected = 2 if "mysql_stmt_bind_named_param" in old else 1
    if count != expected:
        sys.exit(f"{rel}: expected {expected} match(es), found {count}: {old[:60]!r}")
    path.write_text(text.replace(old, new))
    print(f"patched {rel}")
