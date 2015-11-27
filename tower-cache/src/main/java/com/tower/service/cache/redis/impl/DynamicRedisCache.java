package com.tower.service.cache.redis.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.builder.StandardToStringStyle;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;

import com.tower.service.cache.ICache;
import com.tower.service.config.IConfigListener;
import com.tower.service.config.PrefixPriorityConfig;
import com.tower.service.config.dict.ConfigFileTypeDict;
import com.tower.service.util.DateUtil;

public class DynamicRedisCache extends PrefixPriorityConfig
        implements
            Cache,
            ICache,
            IConfigListener {
	/**
	 * Logger for this class
	 */

    private ShardedJedisPool delegate;

    public static final String DEFAULT_CACHE_NAME = "defaultRedisCache";

    @Override
    @PostConstruct
    public void init() {
		if (logger.isDebugEnabled()) {
			logger.debug("init() - start"); //$NON-NLS-1$
		}

        this.setFileName(System.getProperty(CACHE_REDIS_CONFIG, DEFAULT_CACHE_REDIS_CONFIG_NAME));
        this.setType(ConfigFileTypeDict.PROPERTIES);
        super.init();
        this.build(this.getConfig());

		if (logger.isDebugEnabled()) {
			logger.debug("init() - end"); //$NON-NLS-1$
		}
    }

    @Override
    protected String configToString(Configuration config) {
		if (logger.isDebugEnabled()) {
			logger.debug("configToString(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Configuration config", config).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        String prefix_ = getPrefix();

        StringBuffer sb = new StringBuffer();
        sb.append(config.getInt(prefix_ + "redis.timeout")).append("|");
        sb.append(config.getInt(prefix_ + "redis.maxTotal")).append("|");
        sb.append(config.getInt(prefix_ + "redis.minIdle")).append("|");
        sb.append(config.getInt(prefix_ + "redis.maxIdle")).append("|");
        sb.append(config.getInt(prefix_ + "redis.minEvictableIdleTimeMillis")).append("|");
        sb.append(config.getInt(prefix_ + "redis.numTestsPerEvictionRun")).append("|");
        sb.append(config.getInt(prefix_ + "redis.softMinEvictableIdleTimeMillis")).append("|");
        sb.append(config.getBoolean(prefix_ + "redis.testOnBorrow")).append("|");
        sb.append(config.getBoolean(prefix_ + "redis.testOnReturn")).append("|");
        sb.append(config.getBoolean(prefix_ + "redis.testWhileIdle")).append("|");
        sb.append(config.getLong(prefix_ + "redis.timeBetweenEvictionRunsMillis")).append("|");
        String servers[] = config.getStringArray(prefix_ + "redis.servers");
        String ports[] = config.getStringArray(prefix_ + "redis.ports");
        sb.append(arrayToString(servers)).append("|");
        sb.append(arrayToString(ports));

		String returnString = sb.toString();
		if (logger.isDebugEnabled()) {
			logger.debug("configToString(Configuration) - end"); //$NON-NLS-1$
		}
        return returnString;

    }

    private String arrayToString(Object[] objs) {
		if (logger.isDebugEnabled()) {
			logger.debug("arrayToString(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Object[] objs", objs).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        StringBuffer sb = new StringBuffer();
        int len = objs == null ? 0 : objs.length;
        for (int i = 0; i < len; i++) {
            sb.append(objs[i].toString());
            if (i < len - 1) {
                sb.append("|");
            }
        }
		String returnString = sb.toString();
		if (logger.isDebugEnabled()) {
			logger.debug("arrayToString(Object[]) - end"); //$NON-NLS-1$
		}
        return returnString;
    }

    @Override
    protected void build(Configuration config) {
		if (logger.isDebugEnabled()) {
			logger.debug("build(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Configuration config", config).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        String prefix_ = this.getPrefix();

        JedisPoolConfig redisCfg = new JedisPoolConfig();
        redisCfg.setMaxTotal(config.getInt(prefix_ + "redis.maxTotal"));
        redisCfg.setMinIdle(config.getInt(prefix_ + "redis.minIdle"));
        redisCfg.setMaxIdle(config.getInt(prefix_ + "redis.maxIdle"));
        redisCfg.setMinEvictableIdleTimeMillis(config.getInt(prefix_
                + "redis.minEvictableIdleTimeMillis"));
        redisCfg.setNumTestsPerEvictionRun(config.getInt(prefix_ + "redis.numTestsPerEvictionRun"));
        redisCfg.setSoftMinEvictableIdleTimeMillis(config.getInt(prefix_
                + "redis.softMinEvictableIdleTimeMillis"));
        redisCfg.setTestOnBorrow(config.getBoolean(prefix_ + "redis.testOnBorrow"));
        redisCfg.setTestOnReturn(config.getBoolean(prefix_ + "redis.testOnReturn"));
        redisCfg.setTestWhileIdle(config.getBoolean(prefix_ + "redis.testWhileIdle"));
        redisCfg.setTimeBetweenEvictionRunsMillis(config.getLong(prefix_
                + "redis.timeBetweenEvictionRunsMillis"));
        String servers[] = config.getStringArray(prefix_ + "redis.servers");
        String ports[] = config.getStringArray(prefix_ + "redis.ports");

        int ssize = servers.length;
        List<JedisShardInfo> list = new LinkedList<JedisShardInfo>();
        for (int i = 0; i < ssize; i++) {
            JedisShardInfo jedisShardInfo = null;
            String server = (String) servers[i];
            String port = (String) ports[i];
            jedisShardInfo = new JedisShardInfo(server, port);
            jedisShardInfo.setTimeout(config.getInt(prefix_ + "redis.timeout"));
            list.add(jedisShardInfo);
        }
        ShardedJedisPool jedis = new ShardedJedisPool(redisCfg, list);
        if (delegate == null) {
            delegate = jedis;
        } else {
            ShardedJedisPool old = delegate;
            delegate = jedis;
            old.destroy();
        }

		if (logger.isDebugEnabled()) {
			logger.debug("build(Configuration) - end"); //$NON-NLS-1$
		}
    }

    @Override
    public boolean set(String key, Object item) {
		if (logger.isDebugEnabled()) {
			logger.debug("set(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Object item", item).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            _jedis.set(toBytes(key), toBytes(item));

			if (logger.isDebugEnabled()) {
				logger.debug("set(String, Object) - end"); //$NON-NLS-1$
			}
            return true;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
    }

    public boolean set(String key, String item) {
		if (logger.isDebugEnabled()) {
			logger.debug("set(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("String item", item).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            _jedis.set(key, item);

			if (logger.isDebugEnabled()) {
				logger.debug("set(String, String) - end"); //$NON-NLS-1$
			}
            return true;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
    }

    @Override
    public boolean set(String key, Object item, int seconds) {
		if (logger.isDebugEnabled()) {
			logger.debug("set(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Object item", item).append("int seconds", seconds).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            _jedis.set(toBytes(key), toBytes(item));
            _jedis.expire(toBytes(key), seconds);

			if (logger.isDebugEnabled()) {
				logger.debug("set(String, Object, int) - end"); //$NON-NLS-1$
			}
            return true;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
    }

    @Override
    public boolean set(String key, Object item, Date expiry) {
		if (logger.isDebugEnabled()) {
			logger.debug("set(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Object item", item).append("Date expiry", expiry).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		}

        if (expiry == null) {
			boolean returnboolean = set(key, item);
			if (logger.isDebugEnabled()) {
				logger.debug("set(String, Object, Date) - end"); //$NON-NLS-1$
			}
            return returnboolean;
        } else {
            ShardedJedis _jedis = null;
            try {
                _jedis = delegate.getResource();
                _jedis.set(toBytes(key), toBytes(item));
                _jedis.expireAt(toBytes(key), DateUtil.toSecond(expiry));

				if (logger.isDebugEnabled()) {
					logger.debug("set(String, Object, Date) - end"); //$NON-NLS-1$
				}
                return true;
            } finally {
                if (_jedis != null) {
                    delegate.returnResource(_jedis);
                }
            }
        }
    }

    @Override
    public boolean add(String key, Object item) {
		if (logger.isDebugEnabled()) {
			logger.debug("add(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Object item", item).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

		boolean returnboolean = set(key, item);
		if (logger.isDebugEnabled()) {
			logger.debug("add(String, Object) - end"); //$NON-NLS-1$
		}
        return returnboolean;
    }

    @Override
    public boolean add(String key, Object item, int seconds) {
		if (logger.isDebugEnabled()) {
			logger.debug("add(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Object item", item).append("int seconds", seconds).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		}

		boolean returnboolean = set(key, item, seconds);
		if (logger.isDebugEnabled()) {
			logger.debug("add(String, Object, int) - end"); //$NON-NLS-1$
		}
        return returnboolean;
    }

    @Override
    public boolean add(String key, Object item, Date expiry) {
		if (logger.isDebugEnabled()) {
			logger.debug("add(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Object item", item).append("Date expiry", expiry).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		}

		boolean returnboolean = set(key, item, expiry);
		if (logger.isDebugEnabled()) {
			logger.debug("add(String, Object, Date) - end"); //$NON-NLS-1$
		}
        return returnboolean;
    }

    @Override
    public boolean storeCounter(String key, Long counter) {
		if (logger.isDebugEnabled()) {
			logger.debug("storeCounter(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Long counter", counter).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

		boolean returnboolean = this.set(key, counter);
		if (logger.isDebugEnabled()) {
			logger.debug("storeCounter(String, Long) - end"); //$NON-NLS-1$
		}
        return returnboolean;
    }

    @Override
    public long addOrIncr(String key, long incr) {
		if (logger.isDebugEnabled()) {
			logger.debug("addOrIncr(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("long incr", incr).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
			long returnlong = _jedis.incrBy(key, incr);
			if (logger.isDebugEnabled()) {
				logger.debug("addOrIncr(String, long) - end"); //$NON-NLS-1$
			}
            return returnlong;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
    }

    @Override
    public long incr(String key, long incr) {
		if (logger.isDebugEnabled()) {
			logger.debug("incr(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("long incr", incr).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

		long returnlong = addOrIncr(key, incr);
		if (logger.isDebugEnabled()) {
			logger.debug("incr(String, long) - end"); //$NON-NLS-1$
		}
        return returnlong;
    }

    @Override
    public boolean replace(String key, Object item) {
		if (logger.isDebugEnabled()) {
			logger.debug("replace(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Object item", item).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

		boolean returnboolean = this.set(key, item);
		if (logger.isDebugEnabled()) {
			logger.debug("replace(String, Object) - end"); //$NON-NLS-1$
		}
        return returnboolean;
    }

    @Override
    public boolean replace(String key, Object item, int expiry) {
		if (logger.isDebugEnabled()) {
			logger.debug("replace(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Object item", item).append("int expiry", expiry).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		}

		boolean returnboolean = this.set(key, item, expiry);
		if (logger.isDebugEnabled()) {
			logger.debug("replace(String, Object, int) - end"); //$NON-NLS-1$
		}
        return returnboolean;
    }

    public void sadd(String key, String val) {
		if (logger.isDebugEnabled()) {
			logger.debug("sadd(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("String val", val).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            _jedis.sadd(key, val);
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }

		if (logger.isDebugEnabled()) {
			logger.debug("sadd(String, String) - end"); //$NON-NLS-1$
		}
    }

    public void sadd(String key, String val, int expire) {
		if (logger.isDebugEnabled()) {
			logger.debug("sadd(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("String val", val).append("int expire", expire).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            _jedis.sadd(key, val);
            _jedis.expire(key, expire);
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }

		if (logger.isDebugEnabled()) {
			logger.debug("sadd(String, String, int) - end"); //$NON-NLS-1$
		}
    }

    public Set<String> sget(String key) {
		if (logger.isDebugEnabled()) {
			logger.debug("sget(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
			Set<String> returnSet = _jedis.smembers(key);
			if (logger.isDebugEnabled()) {
				logger.debug("sget(String) - end"); //$NON-NLS-1$
			}
            return returnSet;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
    }

    @Override
    public boolean replace(String key, Object item, Date expiry) {
		if (logger.isDebugEnabled()) {
			logger.debug("replace(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).append("Object item", item).append("Date expiry", expiry).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		}

		boolean returnboolean = this.set(key, item, expiry);
		if (logger.isDebugEnabled()) {
			logger.debug("replace(String, Object, Date) - end"); //$NON-NLS-1$
		}
        return returnboolean;
    }

    @Override
    public boolean delete(String key) {
		if (logger.isDebugEnabled()) {
			logger.debug("delete(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            _jedis.del(toBytes(key));

			if (logger.isDebugEnabled()) {
				logger.debug("delete(String) - end"); //$NON-NLS-1$
			}
            return true;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
    }

    @Override
    public String get(String key) {
		if (logger.isDebugEnabled()) {
			logger.debug("get(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String key", key).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
			String returnString = _jedis.get(key);
			if (logger.isDebugEnabled()) {
				logger.debug("get(String) - end"); //$NON-NLS-1$
			}
            return returnString;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
    }

    @Override
    public Object[] get(String[] keys) {
		if (logger.isDebugEnabled()) {
			logger.debug("get(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("String[] keys", keys).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

		if (logger.isDebugEnabled()) {
			logger.debug("get(String[]) - end"); //$NON-NLS-1$
		}
        return null;
    }

    @Override
    public boolean flush() {
        return false;
    }

    private String cacheName = DEFAULT_CACHE_NAME;

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    public String getName() {
        return cacheName;
    }

    @Override
    public Object getNativeCache() {
        return delegate;
    }

    public Object llen(Object key) {
		if (logger.isDebugEnabled()) {
			logger.debug("llen(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Object key", key).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            long length = _jedis.llen(toBytes(key));
			Object returnObject = new SimpleValueWrapper(Long.valueOf(length));
			if (logger.isDebugEnabled()) {
				logger.debug("llen(Object) - end"); //$NON-NLS-1$
			}
            return returnObject;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
    }

    @Override
    public ValueWrapper get(Object key) {
		if (logger.isDebugEnabled()) {
			logger.debug("get(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Object key", key).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            byte[] datas = _jedis.get(toBytes(key));
            if (datas == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("get(Object) - end"); //$NON-NLS-1$
				}
                return null;
            }
            Object result = toObject(datas);
			ValueWrapper returnValueWrapper = new SimpleValueWrapper(result);
			if (logger.isDebugEnabled()) {
				logger.debug("get(Object) - end"); //$NON-NLS-1$
			}
            return returnValueWrapper;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
		if (logger.isDebugEnabled()) {
			logger.debug("get(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Object key", key).append("Class<T> type", type).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            byte[] datas = _jedis.get(toBytes(key));
            if (datas == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("get(Object, Class<T>) - end"); //$NON-NLS-1$
				}
                return null;
            }
            Object result = toObject(datas);
            if (datas != null && type != null && !type.isInstance(result)) {
                throw new IllegalStateException("Cached value is not of required type ["
                        + type.getName() + "]: " + result);
            }
			T returnT = (T) result;
			if (logger.isDebugEnabled()) {
				logger.debug("get(Object, Class<T>) - end"); //$NON-NLS-1$
			}
            return returnT;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }

    }

    @Override
    public void put(Object key, Object value) {
		if (logger.isDebugEnabled()) {
			logger.debug("put(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Object key", key).append("Object value", value).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            _jedis.set(toBytes(key), toBytes(value));
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }

		if (logger.isDebugEnabled()) {
			logger.debug("put(Object, Object) - end"); //$NON-NLS-1$
		}
    }

    @Override
    public void evict(Object key) {
		if (logger.isDebugEnabled()) {
			logger.debug("evict(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Object key", key).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            _jedis.del(toBytes(key));
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }

		if (logger.isDebugEnabled()) {
			logger.debug("evict(Object) - end"); //$NON-NLS-1$
		}
    }

    private byte[] toBytes(Object obj) {
		if (logger.isDebugEnabled()) {
			logger.debug("toBytes(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Object obj", obj).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            byte[] bytes = baos.toByteArray();

			if (logger.isDebugEnabled()) {
				logger.debug("toBytes(Object) - end"); //$NON-NLS-1$
			}
            return bytes;
        } catch (IOException e) {
			logger.warn("toBytes(Object) - exception ignored", e); //$NON-NLS-1$
		} finally {
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
					logger.warn("toBytes(Object) - exception ignored", e); //$NON-NLS-1$
				}
            }
        }

		if (logger.isDebugEnabled()) {
			logger.debug("toBytes(Object) - end"); //$NON-NLS-1$
		}
        return null;
    }

    private Object toObject(byte[] datas) {
		if (logger.isDebugEnabled()) {
			logger.debug("toObject(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("byte[] datas", datas).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

        if (datas == null) {
			if (logger.isDebugEnabled()) {
				logger.debug("toObject(byte[]) - end"); //$NON-NLS-1$
			}
            return null;
        }
        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;
        try {
            bais = new ByteArrayInputStream(datas);
            ois = new ObjectInputStream(bais);
            Object data = ois.readObject();

			if (logger.isDebugEnabled()) {
				logger.debug("toObject(byte[]) - end"); //$NON-NLS-1$
			}
            return data;
        } catch (Exception e) {
            logger.error(e);
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
					logger.warn("toObject(byte[]) - exception ignored", e); //$NON-NLS-1$
				}
            }
        }

		if (logger.isDebugEnabled()) {
			logger.debug("toObject(byte[]) - end"); //$NON-NLS-1$
		}
        return null;
    }

	public ValueWrapper putIfAbsent(Object key, Object value) {
		if (logger.isDebugEnabled()) {
			logger.debug("putIfAbsent(" + new ToStringBuilder("", StandardToStringStyle.SIMPLE_STYLE).append("Object key", key).append("Object value", value).toString() + ") - start"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}
		
		ShardedJedis _jedis = null;
        try {
            _jedis = delegate.getResource();
            byte[] datas = _jedis.get(toBytes(key));
            Object result = toObject(datas);
            if(result==null){
            	_jedis.set(toBytes(key), toBytes(value));
				ValueWrapper returnValueWrapper = new SimpleValueWrapper(value);
				if (logger.isDebugEnabled()) {
					logger.debug("putIfAbsent(Object, Object) - end"); //$NON-NLS-1$
				}
            	return returnValueWrapper;
            }
			ValueWrapper returnValueWrapper = new SimpleValueWrapper(result);
			if (logger.isDebugEnabled()) {
				logger.debug("putIfAbsent(Object, Object) - end"); //$NON-NLS-1$
			}
            return returnValueWrapper;
        } finally {
            if (_jedis != null) {
                delegate.returnResource(_jedis);
            }
        }
	}

}
