package com.alsaril.codegen.constantpool

import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.FIELD
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.INTERFACE_METHOD
import com.alsaril.codegen.constantpool.UpdatableConstantPool.RefType.METHOD
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UpdatableConstantPoolTest {

    private val pool = UpdatableConstantPool()

    @Nested
    inner class PutUtf8 {

        @Test
        fun `stores the value and returns its index`() {
            // when
            val index = pool.putUtf8("hello")

            // then
            assertThat(index).isEqualTo(1)
            assertThat(pool.build().entries).containsExactly(ConstantUtf8Info("hello"))
        }

        @Test
        fun `assigns consecutive indices to distinct values`() {
            // when
            val first = pool.putUtf8("a")
            val second = pool.putUtf8("b")

            // then
            assertThat(first).isEqualTo(1)
            assertThat(second).isEqualTo(2)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("a"),
                ConstantUtf8Info("b"),
            )
        }

        @Test
        fun `returns the existing index for a duplicate value`() {
            // given
            val first = pool.putUtf8("dup")

            // when
            val second = pool.putUtf8("dup")

            // then
            assertThat(second).isEqualTo(first)
            assertThat(pool.build().entries).containsExactly(ConstantUtf8Info("dup"))
        }
    }

    @Nested
    inner class PutInt {

        @Test
        fun `stores the value and returns its index`() {
            // when
            val index = pool.putInt(42)

            // then
            assertThat(index).isEqualTo(1)
            assertThat(pool.build().entries).containsExactly(ConstantIntegerInfo(42))
        }

        @Test
        fun `returns the existing index for a duplicate value`() {
            // given
            val first = pool.putInt(7)

            // when
            val second = pool.putInt(7)

            // then
            assertThat(second).isEqualTo(first)
            assertThat(pool.build().entries).containsExactly(ConstantIntegerInfo(7))
        }

        @Test
        fun `keeps distinct values apart`() {
            // when
            val first = pool.putInt(1)
            val second = pool.putInt(2)

            // then
            assertThat(first).isNotEqualTo(second)
            assertThat(pool.build().entries).containsExactly(
                ConstantIntegerInfo(1),
                ConstantIntegerInfo(2),
            )
        }
    }

    @Nested
    inner class PutFloat {

        @Test
        fun `stores the value and returns its index`() {
            // when
            val index = pool.putFloat(1.5f)

            // then
            assertThat(index).isEqualTo(1)
            assertThat(pool.build().entries).containsExactly(ConstantFloatInfo(1.5f))
        }

        @Test
        fun `occupies a single constant pool slot`() {
            // when a float is one slot wide, unlike a long or a double
            val float = pool.putFloat(1.5f)
            val next = pool.putUtf8("after")

            // then
            assertThat(float).isEqualTo(1)
            assertThat(next).isEqualTo(2)
            assertThat(pool.build().size).isEqualTo(3)
        }

        @Test
        fun `returns the existing index for a duplicate value`() {
            // given
            val first = pool.putFloat(2.5f)

            // when
            val second = pool.putFloat(2.5f)

            // then
            assertThat(second).isEqualTo(first)
            assertThat(pool.build().entries).containsExactly(ConstantFloatInfo(2.5f))
        }

        @Test
        fun `keeps a float apart from an integer of the same value`() {
            // when
            val int = pool.putInt(1)
            val float = pool.putFloat(1.0f)

            // then
            assertThat(float).isNotEqualTo(int)
            assertThat(pool.build().entries).containsExactly(
                ConstantIntegerInfo(1),
                ConstantFloatInfo(1.0f),
            )
        }
    }

    @Nested
    inner class PutLong {

        @Test
        fun `stores the value and returns its index`() {
            // when
            val index = pool.putLong(42L)

            // then
            assertThat(index).isEqualTo(1)
            assertThat(pool.build().entries).containsExactly(ConstantLongInfo(42L))
        }

        @Test
        fun `occupies two constant pool slots`() {
            // when
            val long = pool.putLong(1L)
            val next = pool.putUtf8("after")

            // then
            assertThat(long).isEqualTo(1)
            assertThat(next).isEqualTo(3)
            assertThat(pool.build().size).isEqualTo(4)
        }

        @Test
        fun `returns the existing index for a duplicate value`() {
            // given
            val first = pool.putLong(9L)

            // when
            val second = pool.putLong(9L)

            // then
            assertThat(second).isEqualTo(first)
            assertThat(pool.build().entries).containsExactly(ConstantLongInfo(9L))
        }
    }

    @Nested
    inner class PutDouble {

        @Test
        fun `stores the value and returns its index`() {
            // when
            val index = pool.putDouble(1.5)

            // then
            assertThat(index).isEqualTo(1)
            assertThat(pool.build().entries).containsExactly(ConstantDoubleInfo(1.5))
        }

        @Test
        fun `occupies two constant pool slots`() {
            // when
            val double = pool.putDouble(1.5)
            val next = pool.putUtf8("after")

            // then
            assertThat(double).isEqualTo(1)
            assertThat(next).isEqualTo(3)
            assertThat(pool.build().size).isEqualTo(4)
        }

        @Test
        fun `returns the existing index for a duplicate value`() {
            // given
            val first = pool.putDouble(2.5)

            // when
            val second = pool.putDouble(2.5)

            // then
            assertThat(second).isEqualTo(first)
            assertThat(pool.build().entries).containsExactly(ConstantDoubleInfo(2.5))
        }
    }

    @Nested
    inner class PutClass {

        @Test
        fun `stores the name as utf8 and points the class entry at it`() {
            // when
            val index = pool.putClass("java/lang/String")

            // then
            assertThat(index).isEqualTo(2)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("java/lang/String"),
                ConstantClassInfo(nameIndex = 1),
            )
        }

        @Test
        fun `returns the existing index for a duplicate name`() {
            // given
            val first = pool.putClass("A")

            // when
            val second = pool.putClass("A")

            // then
            assertThat(second).isEqualTo(first)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
            )
        }

        @Test
        fun `reuses a utf8 entry that already exists`() {
            // given
            val utf8 = pool.putUtf8("A")

            // when
            val clazz = pool.putClass("A")

            // then
            assertThat(utf8).isEqualTo(1)
            assertThat(clazz).isEqualTo(2)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
            )
        }
    }

    @Nested
    inner class PutString {

        @Test
        fun `stores the value as utf8 and points the string entry at it`() {
            // when
            val index = pool.putString("hello")

            // then
            assertThat(index).isEqualTo(2)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("hello"),
                ConstantStringInfo(valueIndex = 1),
            )
        }

        @Test
        fun `returns the existing index for a duplicate value`() {
            // given
            val first = pool.putString("dup")

            // when
            val second = pool.putString("dup")

            // then
            assertThat(second).isEqualTo(first)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("dup"),
                ConstantStringInfo(valueIndex = 1),
            )
        }

        @Test
        fun `shares the utf8 entry with an equal class name but stays a distinct entry`() {
            // given
            val clazz = pool.putClass("A")

            // when
            val string = pool.putString("A")

            // then
            assertThat(string).isNotEqualTo(clazz)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
                ConstantStringInfo(valueIndex = 1),
            )
        }
    }

    @Nested
    inner class PutConstantNameAndTypeInfo {

        @Test
        fun `stores name and descriptor as utf8 entries`() {
            // when
            val index = pool.putConstantNameAndTypeInfo("size", "()I")

            // then
            assertThat(index).isEqualTo(3)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("size"),
                ConstantUtf8Info("()I"),
                ConstantNameAndTypeInfo(nameIndex = 1, descriptorIndex = 2),
            )
        }

        @Test
        fun `returns the existing index for a duplicate name and descriptor`() {
            // given
            val first = pool.putConstantNameAndTypeInfo("size", "()I")

            // when
            val second = pool.putConstantNameAndTypeInfo("size", "()I")

            // then
            assertThat(second).isEqualTo(first)
            assertThat(pool.build().entries).hasSize(3)
        }

        @Test
        fun `keeps the same name with a different descriptor apart while sharing the name utf8`() {
            // given
            val first = pool.putConstantNameAndTypeInfo("f", "()I")

            // when
            val second = pool.putConstantNameAndTypeInfo("f", "()J")

            // then
            assertThat(second).isNotEqualTo(first)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("f"),
                ConstantUtf8Info("()I"),
                ConstantNameAndTypeInfo(nameIndex = 1, descriptorIndex = 2),
                ConstantUtf8Info("()J"),
                ConstantNameAndTypeInfo(nameIndex = 1, descriptorIndex = 4),
            )
        }

        @Test
        fun `reuses a single utf8 entry when name and descriptor are equal`() {
            // when
            val index = pool.putConstantNameAndTypeInfo("I", "I")

            // then
            assertThat(index).isEqualTo(2)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("I"),
                ConstantNameAndTypeInfo(nameIndex = 1, descriptorIndex = 1),
            )
        }
    }

    @Nested
    inner class PutRef {

        @Test
        fun `stores a field ref pointing at class and name-and-type`() {
            // given
            val clazz = pool.putClass("A")

            // when
            val ref = pool.putRef(clazz, "field", "I", FIELD)

            // then
            assertThat(ref).isEqualTo(6)
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info("field"),
                ConstantUtf8Info("I"),
                ConstantNameAndTypeInfo(nameIndex = 3, descriptorIndex = 4),
                ConstantFieldRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
            )
        }

        @Test
        fun `stores a method ref`() {
            // given
            val clazz = pool.putClass("A")

            // when
            pool.putRef(clazz, "f", "()V", METHOD)

            // then
            assertThat(pool.build().entries).last()
                .isEqualTo(ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5))
        }

        @Test
        fun `stores an interface method ref`() {
            // given
            val clazz = pool.putClass("A")

            // when
            pool.putRef(clazz, "f", "()V", INTERFACE_METHOD)

            // then
            assertThat(pool.build().entries).last()
                .isEqualTo(ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5))
        }

        @Test
        fun `returns the existing index for a duplicate ref`() {
            // given
            val clazz = pool.putClass("A")
            val first = pool.putRef(clazz, "f", "()V", METHOD)

            // when
            val second = pool.putRef(clazz, "f", "()V", METHOD)

            // then
            assertThat(second).isEqualTo(first)
            assertThat(pool.build().entries).hasSize(6)
        }

        @Test
        fun `keeps ref types apart while sharing one name-and-type`() {
            // given
            val clazz = pool.putClass("A")

            // when
            val field = pool.putRef(clazz, "f", "()V", FIELD)
            val method = pool.putRef(clazz, "f", "()V", METHOD)
            val interfaceMethod = pool.putRef(clazz, "f", "()V", INTERFACE_METHOD)

            // then
            assertThat(listOf(field, method, interfaceMethod)).doesNotHaveDuplicates()
            assertThat(pool.build().entries).containsExactly(
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 1),
                ConstantUtf8Info("f"),
                ConstantUtf8Info("()V"),
                ConstantNameAndTypeInfo(nameIndex = 3, descriptorIndex = 4),
                ConstantFieldRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
                ConstantInterfaceMethodRefInfo(classNameIndex = 2, nameAndTypeIndex = 5),
            )
        }

        @Test
        fun `keeps the same member on different classes apart`() {
            // given
            val a = pool.putClass("A")
            val b = pool.putClass("B")

            // when
            val first = pool.putRef(a, "f", "()V", METHOD)
            val second = pool.putRef(b, "f", "()V", METHOD)

            // then
            assertThat(second).isNotEqualTo(first)
            assertThat(pool.build().entries).contains(
                ConstantMethodRefInfo(classNameIndex = a, nameAndTypeIndex = 7),
                ConstantMethodRefInfo(classNameIndex = b, nameAndTypeIndex = 7),
            )
        }
    }

    @Nested
    inner class Build {

        @Test
        fun `an empty pool has size 1 and no entries`() {
            // when
            val built = pool.build()

            // then
            assertThat(built.size).isEqualTo(1)
            assertThat(built.entries).isEmpty()
        }

        @Test
        fun `size is the next free index`() {
            // given
            pool.putUtf8("a")
            pool.putUtf8("b")

            // when
            val built = pool.build()

            // then
            assertThat(built.size).isEqualTo(3)
        }

        @Test
        fun `size counts the second slot of long and double entries`() {
            // given
            pool.putLong(1L)
            pool.putDouble(1.0)
            pool.putInt(1)

            // when
            val built = pool.build()

            // then
            assertThat(built.entries).hasSize(3)
            assertThat(built.size).isEqualTo(6)
        }

        @Test
        fun `entries keep insertion order`() {
            // given
            pool.putInt(1)
            pool.putClass("A")
            pool.putUtf8("z")

            // when
            val built = pool.build()

            // then
            assertThat(built.entries).containsExactly(
                ConstantIntegerInfo(1),
                ConstantUtf8Info("A"),
                ConstantClassInfo(nameIndex = 2),
                ConstantUtf8Info("z"),
            )
        }

        @Test
        fun `rejects a new entry after building`() {
            // given
            pool.build()

            // then
            assertThatIllegalStateException().isThrownBy { pool.putUtf8("late") }
            assertThatIllegalStateException().isThrownBy { pool.putInt(1) }
            assertThatIllegalStateException().isThrownBy { pool.putFloat(1.0f) }
            assertThatIllegalStateException().isThrownBy { pool.putLong(1L) }
            assertThatIllegalStateException().isThrownBy { pool.putDouble(1.0) }
            assertThatIllegalStateException().isThrownBy { pool.putClass("A") }
            assertThatIllegalStateException().isThrownBy { pool.putString("s") }
            assertThatIllegalStateException().isThrownBy { pool.putConstantNameAndTypeInfo("f", "()V") }
            assertThatIllegalStateException().isThrownBy { pool.putRef(1, "f", "()V", METHOD) }
        }

        @Test
        fun `still serves already cached values after building`() {
            // given
            val utf8 = pool.putUtf8("cached")
            val clazz = pool.putClass("A")
            pool.build()

            // then
            assertThat(pool.putUtf8("cached")).isEqualTo(utf8)
            assertThat(pool.putClass("A")).isEqualTo(clazz)
        }

        @Test
        fun `returns a snapshot that later failed writes cannot change`() {
            // given
            pool.putUtf8("a")
            val built = pool.build()

            // when
            runCatching { pool.putUtf8("b") }

            // then
            assertThat(built.entries).containsExactly(ConstantUtf8Info("a"))
            assertThat(built.size).isEqualTo(2)
        }
    }
}
