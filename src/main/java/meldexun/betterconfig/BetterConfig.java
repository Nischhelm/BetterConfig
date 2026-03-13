package meldexun.betterconfig;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import meldexun.betterconfig.gui.configuration.ConfigurationGuiRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = BetterConfig.MODID)
public class BetterConfig {

	public static final String MODID = "betterconfig";
	public static final Logger LOGGER = LogManager.getLogger(MODID);
	public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

	@EventHandler
	public void onFMLConstructionEvent(FMLConstructionEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
		NETWORK.registerMessage(SyncConfigPacket.Handler.class, SyncConfigPacket.class, 1, Side.CLIENT);
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onConfigChangedEvent(OnConfigChangedEvent event) {
		MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
		if (server != null) {
			server.addScheduledTask(() -> {
				NETWORK.sendToAll(new SyncConfigPacket(ConfigManager.syncedConfigs()));
			});
		}

		// Configuration GUIs
		ConfigurationGuiRegistry.save(event.getModID());
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onPlayerLoggedInEvent(PlayerLoggedInEvent event) {
		NETWORK.sendTo(new SyncConfigPacket(ConfigManager.syncedConfigs()), (EntityPlayerMP) event.player);
	}

	public static class SyncConfigPacket implements IMessage {

		private final Map<String, ByteBuf> syncedConfigClasses = new LinkedHashMap<>();

		public SyncConfigPacket() {

		}

		public SyncConfigPacket(Class<?>[] configClasses) {
			for (Class<?> configClass : configClasses) {
				this.syncedConfigClasses.put(configClass.getName(), write(configClass));
			}
		}

		@Override
		public void fromBytes(ByteBuf buf) {
			for (int i = buf.readInt(); i > 0; i--) {
				this.syncedConfigClasses.put(ByteBufUtils.readUTF8String(buf), buf.readBytes(buf.readInt()));
			}
		}

		@Override
		public void toBytes(ByteBuf buf) {
			buf.writeInt(this.syncedConfigClasses.size());
			this.syncedConfigClasses.forEach((k, v) -> {
				ByteBufUtils.writeUTF8String(buf, k);
				buf.writeInt(v.readableBytes());
				buf.writeBytes(v);
			});
		}

		public static class Handler implements IMessageHandler<SyncConfigPacket, IMessage> {

			@Override
			public IMessage onMessage(SyncConfigPacket message, MessageContext ctx) {
				FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
					message.syncedConfigClasses.forEach((k, v) -> {
						try {
							read(Class.forName(k), null, v);
						} catch (Exception e) {
							LOGGER.error("Failed reading config data from server for class {}", k, e);
						}
					});
				});
				return null;
			}

		}

	}

	private static ByteBuf write(Class<?> configClass) {
		ByteBuf buf = Unpooled.buffer();
		write(configClass, null, buf);
		return buf;
	}

	private static void write(Type type, @Nullable Object instance, ByteBuf buf) {
		if (TypeAdapters.hasAdapter(type)) {
			ByteBufUtils.writeUTF8String(buf, TypeAdapters.get(type).serialize(instance));
		} else if (TypeUtil.isArray(type)) {
			Type componentType = TypeUtil.getComponentType(type);
			buf.writeInt(Array.getLength(instance));
			for (int i = 0; i < Array.getLength(instance); i++) {
				write(componentType, Array.get(instance, i), buf);
			}
		} else if (TypeUtil.isCollection(type)) {
			Type elementType = TypeUtil.getElementType(type);
			buf.writeInt(((Collection<?>) instance).size());
			for (Object element : ((Collection<?>) instance)) {
				write(elementType, element, buf);
			}
		} else if (TypeUtil.isMap(type)) {
			Type keyType = TypeUtil.getKeyType(type);
			Type valueType = TypeUtil.getValueType(type);
			buf.writeInt(((Map<?, ?>) instance).size());
			((Map<?, ?>) instance).forEach((k, v) -> {
				write(keyType, k, buf);
				write(valueType, v, buf);
			});
		} else {
			Field[] fields = ConfigUtil.getConfigFields(type, instance == null);
			buf.writeInt(fields.length);
			Arrays.stream(fields)
					.sorted(Comparator.comparing(Field::getName))
					.forEach(field -> {
						ByteBufUtils.writeUTF8String(buf, field.getName());
						try {
							write(field.getGenericType(), field.get(instance), buf);
						} catch (ReflectiveOperationException e) {
							throw new UnsupportedOperationException(e);
						}
					});
		}
	}

	@SuppressWarnings("unchecked")
	private static Object read(Type type, @Nullable Object instance, ByteBuf buf) {
		if (TypeAdapters.hasAdapter(type)) {
			return TypeAdapters.get(type).deserialize(ByteBufUtils.readUTF8String(buf));
		} else if (TypeUtil.isArray(type)) {
			Type componentType = TypeUtil.getComponentType(type);
			Object array = Array.newInstance(TypeUtil.getRawType(componentType), buf.readInt());
			for (int i = 0; i < Array.getLength(array); i++) {
				Array.set(array, i, read(componentType, TypeUtil.newInstance(componentType), buf));
			}
			return array;
		} else if (TypeUtil.isCollection(type)) {
			Type elementType = TypeUtil.getElementType(type);
			Collection<Object> collection = (Collection<Object>) TypeUtil.newInstance(type, instance);
			for (int i = buf.readInt(); i > 0; i--) {
				collection.add(read(elementType, TypeUtil.newInstance(elementType), buf));
			}
			return collection;
		} else if (TypeUtil.isMap(type)) {
			Type keyType = TypeUtil.getKeyType(type);
			Type valueType = TypeUtil.getValueType(type);
			Map<Object, Object> map = (Map<Object, Object>) TypeUtil.newInstance(type, instance);
			for (int i = buf.readInt(); i > 0; i--) {
				map.put(read(keyType, TypeUtil.newInstance(keyType), buf), read(valueType, TypeUtil.newInstance(valueType), buf));
			}
			return map;
		} else {
			Map<String, Field> fields = Arrays.stream(ConfigUtil.getConfigFields(type, instance == null)).collect(Collectors.toMap(Field::getName, Function.identity()));
			for (int i = buf.readInt(); i > 0; i--) {
				Field field = fields.get(ByteBufUtils.readUTF8String(buf));
				try {
					field.set(instance, read(field.getGenericType(), field.get(instance), buf));
				} catch (ReflectiveOperationException e) {
					throw new UnsupportedOperationException(e);
				}
			}
			return instance;
		}
	}

}
