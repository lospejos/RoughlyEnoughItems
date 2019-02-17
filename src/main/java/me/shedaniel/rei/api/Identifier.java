package me.shedaniel.rei.api;

import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.text.TextComponentTranslation;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Type;

public class Identifier implements Comparable<Identifier> {
    
    private static final SimpleCommandExceptionType COMMAND_EXCEPTION_TYPE = new SimpleCommandExceptionType(new TextComponentTranslation("argument.id.invalid", new Object[0]));
    protected final String namespace;
    protected final String path;
    
    protected Identifier(String[] resourceNames) {
        this.namespace = StringUtils.isEmpty(resourceNames[0]) ? "minecraft" : resourceNames[0];
        this.path = resourceNames[1];
        
        if (!this.namespace.chars().allMatch((p_195825_0_) -> {
            return p_195825_0_ == 95 || p_195825_0_ == 45 || p_195825_0_ >= 97 && p_195825_0_ <= 122 || p_195825_0_ >= 48 && p_195825_0_ <= 57 || p_195825_0_ == 46;
        })) {
            throw new IllegalArgumentException("Non [a-z0-9_.-] character in namespace of location: " + this.namespace + ':' + this.path);
        } else if (!this.path.chars().allMatch((p_195827_0_) -> {
            return p_195827_0_ == 95 || p_195827_0_ == 45 || p_195827_0_ >= 97 && p_195827_0_ <= 122 || p_195827_0_ >= 48 && p_195827_0_ <= 57 || p_195827_0_ == 47 || p_195827_0_ == 46;
        })) {
            throw new IllegalArgumentException("Non [a-z0-9/._-] character in path of location: " + this.namespace + ':' + this.path);
        }
    }
    
    public Identifier(String resourceName) {
        this(decompose(resourceName, ':'));
    }
    
    public Identifier(String namespaceIn, String pathIn) {
        this(new String[]{namespaceIn, pathIn});
    }
    
    public static Identifier of(String resourceName, char split) {
        return new Identifier(decompose(resourceName, split));
    }
    
    @Nullable
    public static Identifier makeIdentifier(String string) {
        try {
            return new Identifier(string);
        } catch (IllegalArgumentException var2) {
            return null;
        }
    }
    
    protected static String[] decompose(String p_195823_0_, char p_195823_1_) {
        String[] astring = new String[]{"minecraft", p_195823_0_};
        int i = p_195823_0_.indexOf(p_195823_1_);
        
        if (i >= 0) {
            astring[1] = p_195823_0_.substring(i + 1, p_195823_0_.length());
            
            if (i >= 1) {
                astring[0] = p_195823_0_.substring(0, i);
            }
        }
        
        return astring;
    }
    
    public static Identifier read(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        
        while (reader.canRead() && isValidPathCharacter(reader.peek()))
            reader.skip();
        
        String s = reader.getString().substring(i, reader.getCursor());
        
        try {
            return new Identifier(s);
        } catch (IllegalArgumentException var4) {
            reader.setCursor(i);
            throw COMMAND_EXCEPTION_TYPE.createWithContext(reader);
        }
    }
    
    public static boolean isValidPathCharacter(char charIn) {
        return charIn >= '0' && charIn <= '9' || charIn >= 'a' && charIn <= 'z' || charIn == '_' || charIn == ':' || charIn == '/' || charIn == '.' || charIn == '-';
    }
    
    public String getPath() {
        return this.path;
    }
    
    public String getNamespace() {
        return this.namespace;
    }
    
    public String toString() {
        return this.namespace + ':' + this.path;
    }
    
    public boolean equals(Object p_equals_1_) {
        if (this == p_equals_1_) {
            return true;
        } else if (!(p_equals_1_ instanceof Identifier)) {
            return false;
        } else {
            Identifier identifier = (Identifier) p_equals_1_;
            return this.namespace.equals(identifier.namespace) && this.path.equals(identifier.path);
        }
    }
    
    public int hashCode() {
        return 31 * this.namespace.hashCode() + this.path.hashCode();
    }
    
    public int compareTo(Identifier p_compareTo_1_) {
        int i = this.path.compareTo(p_compareTo_1_.path);
        
        if (i == 0) {
            i = this.namespace.compareTo(p_compareTo_1_.namespace);
        }
        
        return i;
    }
    
    public static class Serializer implements JsonDeserializer<Identifier>, JsonSerializer<Identifier> {
        public Identifier deserialize(JsonElement p_deserialize_1_, Type p_deserialize_2_, JsonDeserializationContext p_deserialize_3_) throws JsonParseException {
            return new Identifier(JsonUtils.getString(p_deserialize_1_, "location"));
        }
        
        public JsonElement serialize(Identifier p_serialize_1_, Type p_serialize_2_, JsonSerializationContext p_serialize_3_) {
            return new JsonPrimitive(p_serialize_1_.toString());
        }
    }
}
