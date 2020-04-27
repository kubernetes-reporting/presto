/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.plugin.accumulo.model;

import io.airlift.slice.Slice;
import io.prestosql.plugin.accumulo.Types;
import io.prestosql.spi.PrestoException;
import io.prestosql.spi.block.ArrayBlock;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.type.Type;
import io.prestosql.spi.type.VarcharType;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.prestosql.spi.StandardErrorCode.FUNCTION_IMPLEMENTATION_ERROR;
import static io.prestosql.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.prestosql.spi.type.BigintType.BIGINT;
import static io.prestosql.spi.type.BooleanType.BOOLEAN;
import static io.prestosql.spi.type.DateType.DATE;
import static io.prestosql.spi.type.DoubleType.DOUBLE;
import static io.prestosql.spi.type.IntegerType.INTEGER;
import static io.prestosql.spi.type.RealType.REAL;
import static io.prestosql.spi.type.SmallintType.SMALLINT;
import static io.prestosql.spi.type.TimeType.TIME;
import static io.prestosql.spi.type.TimestampType.TIMESTAMP;
import static io.prestosql.spi.type.TinyintType.TINYINT;
import static io.prestosql.spi.type.VarbinaryType.VARBINARY;
import static io.prestosql.spi.type.VarcharType.VARCHAR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.DAYS;

public class Field
{
    private final Object value;
    private final Type type;
    private final boolean indexed;

    public Field(Object value, Type type)
    {
        this(value, type, false);
    }

    public Field(Object value, Type type, boolean indexed)
    {
        this.value = cleanObject(value, type);
        this.type = requireNonNull(type, "type is null");
        this.indexed = indexed;
    }

    public Field(Field field)
    {
        this.type = field.type;
        this.indexed = false;

        if (Types.isArrayType(this.type) || Types.isMapType(this.type)) {
            this.value = field.value;
            return;
        }

        if (type.equals(BIGINT)) {
            this.value = field.getLong();
        }
        else if (type.equals(BOOLEAN)) {
            this.value = field.getBoolean();
        }
        else if (type.equals(DATE)) {
            this.value = new Date(field.getDate().getTime());
        }
        else if (type.equals(DOUBLE)) {
            this.value = field.getDouble();
        }
        else if (type.equals(INTEGER)) {
            this.value = field.getInt();
        }
        else if (type.equals(REAL)) {
            this.value = field.getFloat();
        }
        else if (type.equals(SMALLINT)) {
            this.value = field.getShort();
        }
        else if (type.equals(TIME)) {
            this.value = new Time(field.getTime().getTime());
        }
        else if (type.equals(TIMESTAMP)) {
            this.value = new Timestamp(field.getTimestamp().getTime());
        }
        else if (type.equals(TINYINT)) {
            this.value = field.getByte();
        }
        else if (type.equals(VARBINARY)) {
            this.value = Arrays.copyOf(field.getVarbinary(), field.getVarbinary().length);
        }
        else if (type.equals(VARCHAR)) {
            this.value = field.getVarchar();
        }
        else {
            throw new PrestoException(NOT_SUPPORTED, "Unsupported type " + type);
        }
    }

    public Type getType()
    {
        return type;
    }

    public Block getArray()
    {
        return (Block) value;
    }

    public Long getLong()
    {
        return (Long) value;
    }

    public Boolean getBoolean()
    {
        return (Boolean) value;
    }

    public Byte getByte()
    {
        return (Byte) value;
    }

    public Date getDate()
    {
        return (Date) value;
    }

    public Double getDouble()
    {
        return (Double) value;
    }

    public Float getFloat()
    {
        return (Float) value;
    }

    public Integer getInt()
    {
        return (Integer) value;
    }

    public Block getMap()
    {
        return (Block) value;
    }

    public Object getObject()
    {
        return value;
    }

    public Short getShort()
    {
        return (Short) value;
    }

    public Timestamp getTimestamp()
    {
        return (Timestamp) value;
    }

    public Time getTime()
    {
        return (Time) value;
    }

    public byte[] getVarbinary()
    {
        return (byte[]) value;
    }

    public String getVarchar()
    {
        return (String) value;
    }

    public boolean isIndexed()
    {
        return indexed;
    }

    public boolean isNull()
    {
        return value == null;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value, type, indexed);
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean retval = true;
        if (obj instanceof Field) {
            Field field = (Field) obj;
            if (type.equals(field.getType())) {
                if (this.isNull() && field.isNull()) {
                    retval = true;
                }
                else if (this.isNull() != field.isNull()) {
                    retval = false;
                }
                else if (type.equals(VARBINARY)) {
                    // special case for byte arrays
                    // aren't they so fancy
                    retval = Arrays.equals((byte[]) value, (byte[]) field.getObject());
                }
                else if (type.equals(DATE) || type.equals(TIME) || type.equals(TIMESTAMP)) {
                    retval = value.toString().equals(field.getObject().toString());
                }
                else {
                    if (value instanceof Block) {
                        retval = equals((Block) value, (Block) field.getObject());
                    }
                    else {
                        retval = value.equals(field.getObject());
                    }
                }
            }
        }
        return retval;
    }

    private static boolean equals(Block block1, Block block2)
    {
        boolean retval = block1.getPositionCount() == block2.getPositionCount();
        for (int i = 0; i < block1.getPositionCount() && retval; ++i) {
            if (block1 instanceof ArrayBlock && block2 instanceof ArrayBlock) {
                retval = equals(block1.getObject(i, Block.class), block2.getObject(i, Block.class));
            }
            else {
                retval = block1.compareTo(i, 0, block1.getSliceLength(i), block2, i, 0, block2.getSliceLength(i)) == 0;
            }
        }
        return retval;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("value", value)
                .add("type", type)
                .toString();
    }

    /**
     * Does it's damnedest job to convert the given object to the given type.
     *
     * @param value Object to convert
     * @param type Destination Presto type
     * @return Null if null, the converted type of it could convert it, or the same value if it is fine just the way it is :D
     * @throws PrestoException If the given object is not any flavor of the given type
     */
    private static Object cleanObject(Object value, Type type)
    {
        if (value == null) {
            return null;
        }

        // Array? Better be a block!
        if (Types.isArrayType(type)) {
            if (!(value instanceof Block)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Block, but " + value.getClass());
            }
            return value;
        }

        // Map? Better be a block!
        if (Types.isMapType(type)) {
            if (!(value instanceof Block)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Block, but " + value.getClass());
            }
            return value;
        }

        // And now for the plain types
        if (type.equals(BIGINT)) {
            if (!(value instanceof Long)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Long, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(INTEGER)) {
            if (value instanceof Long) {
                return ((Long) value).intValue();
            }

            if (!(value instanceof Integer)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Long or Integer, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(BOOLEAN)) {
            if (!(value instanceof Boolean)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Boolean, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(DATE)) {
            if (value instanceof Long) {
                return new Date(DAYS.toMillis((Long) value));
            }

            if (value instanceof Calendar) {
                return new Date(((Calendar) value).getTime().getTime());
            }

            if (!(value instanceof Date)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Calendar, Date, or Long, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(DOUBLE)) {
            if (!(value instanceof Double)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Double, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(REAL)) {
            if (value instanceof Long) {
                return Float.intBitsToFloat(((Long) value).intValue());
            }

            if (value instanceof Integer) {
                return Float.intBitsToFloat((Integer) value);
            }

            if (!(value instanceof Float)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Float, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(SMALLINT)) {
            if (value instanceof Long) {
                return ((Long) value).shortValue();
            }

            if (value instanceof Integer) {
                return ((Integer) value).shortValue();
            }

            if (!(value instanceof Short)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Short, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(TIME)) {
            if (value instanceof Long) {
                return new Time((Long) value);
            }

            if (!(value instanceof Time)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Long or Time, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(TIMESTAMP)) {
            if (value instanceof Long) {
                return new Timestamp((Long) value);
            }

            if (!(value instanceof Timestamp)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Long or Timestamp, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(TINYINT)) {
            if (value instanceof Long) {
                return ((Long) value).byteValue();
            }

            if (value instanceof Integer) {
                return ((Integer) value).byteValue();
            }

            if (value instanceof Short) {
                return ((Short) value).byteValue();
            }

            if (!(value instanceof Byte)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Byte, but " + value.getClass());
            }
            return value;
        }

        if (type.equals(VARBINARY)) {
            if (value instanceof Slice) {
                return ((Slice) value).getBytes();
            }

            if (!(value instanceof byte[])) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Slice byte[], but " + value.getClass());
            }
            return value;
        }

        if (type instanceof VarcharType) {
            if (value instanceof Slice) {
                return new String(((Slice) value).getBytes(), UTF_8);
            }

            if (!(value instanceof String)) {
                throw new PrestoException(FUNCTION_IMPLEMENTATION_ERROR, "Object is not a Slice or String, but " + value.getClass());
            }
            return value;
        }

        throw new PrestoException(NOT_SUPPORTED, "Unsupported PrestoType " + type);
    }
}
