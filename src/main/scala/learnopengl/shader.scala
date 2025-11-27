package learnopengl.shader

import org.joml.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL32.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import scala.io.Source

trait Shader {

  def ID: Int

  def use(): Unit

  def setBool(name: String, value: Boolean): Unit

  def setInt(name: String, value: Int): Unit

  def setFloat(name: String, value: Float): Unit

  def setVec2(name: String, value: Vector2f): Unit
  def setVec2(name: String, x: Float, y: Float): Unit
  def setMat2(name: String, mat: Matrix2f): Unit

  def setVec3(name: String, value: Vector3f): Unit
  def setVec3(name: String, x: Float, y: Float, z: Float): Unit
  def setMat3(name: String, mat: Matrix3f): Unit

  def setVec4(name: String, value: Vector4f): Unit
  def setVec4(name: String, x: Float, y: Float, z: Float, w: Float): Unit
  def setMat4(name: String, mat: Matrix4f): Unit

}

object Shader {

  def apply(
      vertexPath: String,
      fragmentPath: String,
      geometryPath: String = null
  ) = new Shader {

    // read resource file's contents into streams
    val vShaderStream = getClass.getResourceAsStream(s"/shaders/$vertexPath")
    val fShaderStream = getClass.getResourceAsStream(s"/shaders/$fragmentPath")
    val gShaderStream = getClass.getResourceAsStream(s"/shaders/$geometryPath")
    // convert stream into string
    val vShaderCode =
      try Source.fromInputStream(vShaderStream).mkString
      finally vShaderStream.close()
    val fShaderCode =
      try Source.fromInputStream(fShaderStream).mkString
      finally fShaderStream.close()
    val gShaderCode =
      try Source.fromInputStream(gShaderStream).mkString
      finally gShaderStream.close()

    // vertex shader
    val vertex = glCreateShader(GL_VERTEX_SHADER)
    glShaderSource(vertex, vShaderCode)
    glCompileShader(vertex)
    checkCompileErrors(vertex, "VERTEX")
    // fragment Shader
    val fragment = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(fragment, fShaderCode)
    glCompileShader(fragment)
    checkCompileErrors(fragment, "FRAGMENT")
    // if geometry shader is given, compile geometry shader
    var geometry: Int = 0
    if (geometryPath != null) {
      // const char * gShaderCode = geometryCode.c_str()
      geometry = glCreateShader(GL_GEOMETRY_SHADER)
      glShaderSource(geometry, gShaderCode)
      glCompileShader(geometry)
      checkCompileErrors(geometry, "GEOMETRY")
    }
    // shader Program
    override val ID = glCreateProgram()
    glAttachShader(ID, vertex)
    glAttachShader(ID, fragment)
    if (geometryPath != null)
      glAttachShader(ID, geometry)
    glLinkProgram(ID)
    checkCompileErrors(ID, "PROGRAM")
    // delete the shaders as they're linked into our program now and no longer necessary
    glDeleteShader(vertex)
    glDeleteShader(fragment)
    if (geometryPath != null)
      glDeleteShader(geometry)

    // activate the shader
    // ------------------------------------------------------------------------
    override def use(): Unit = glUseProgram(ID)

    // utility uniform functions
    // ------------------------------------------------------------------------
    override def setBool(name: String, value: Boolean): Unit =
      glUniform1i(glGetUniformLocation(ID, name), if (value) 1 else 0)
    override def setInt(name: String, value: Int): Unit =
      glUniform1i(glGetUniformLocation(ID, name), value)

    override def setFloat(name: String, value: Float): Unit =
      glUniform1f(glGetUniformLocation(ID, name), value)

    override def setVec2(name: String, value: Vector2f): Unit =
      glUniform2f(glGetUniformLocation(ID, name), value.x, value.y)

    override def setVec2(name: String, x: Float, y: Float): Unit =
      glUniform2f(glGetUniformLocation(ID, name), x, y)

    override def setVec3(name: String, value: Vector3f): Unit =
      glUniform3f(glGetUniformLocation(ID, name), value.x, value.y, value.z)

    override def setVec3(name: String, x: Float, y: Float, z: Float): Unit =
      glUniform3f(glGetUniformLocation(ID, name), x, y, z)

    override def setVec4(name: String, value: Vector4f): Unit =
      glUniform4f(
        glGetUniformLocation(ID, name),
        value.x,
        value.y,
        value.z,
        value.w
      )

    override def setVec4(
        name: String,
        x: Float,
        y: Float,
        z: Float,
        w: Float
    ): Unit =
      glUniform4f(glGetUniformLocation(ID, name), x, y, z, w)

    override def setMat2(name: String, mat: Matrix2f): Unit =
      usingStack { stack =>
        val fb = stack.mallocFloat(4)
        mat.get(fb)
        glUniformMatrix2fv(glGetUniformLocation(ID, name), false, fb)
      }

    override def setMat3(name: String, mat: Matrix3f): Unit =
      usingStack { stack =>
        val fb = stack.mallocFloat(9)
        mat.get(fb)
        glUniformMatrix3fv(glGetUniformLocation(ID, name), false, fb)
      }

    override def setMat4(name: String, mat: Matrix4f): Unit =
      usingStack { stack =>
        val fb = stack.mallocFloat(16)
        mat.get(fb)
        glUniformMatrix4fv(glGetUniformLocation(ID, name), false, fb)
      }

    // utility function for checking shader compilation/linking errors.
    // ------------------------------------------------------------------------
    def checkCompileErrors(shader: Int, shaderType: String) = {
      if (shaderType != "PROGRAM") {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
          val log = glGetShaderInfoLog(shader)
          throw new RuntimeException(
            "ERROR::SHADER::FRAGMENT::COMPILATION_FAILED:\n" + log
          )
        }
      } else {
        if (glGetProgrami(shader, GL_LINK_STATUS) == GL_FALSE) {
          val log = glGetShaderInfoLog(shader)
          throw new RuntimeException(
            "ERROR::SHADER::PROGRAM::LINKING_FAILED:\n" + log
          )
        }
      }
    }

    def usingStack[A](f: MemoryStack => A): A =
      val stack = MemoryStack.stackPush()
      try f(stack)
      finally stack.pop()

  }

}
