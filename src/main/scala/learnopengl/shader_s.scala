package learnopengl.shader_s

import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
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

}

object Shader {

  def apply(vertexPath: String, fragmentPath: String) = new Shader {

    // read resource file's contents into streams
    val vShaderStream = getClass.getResourceAsStream(s"/shaders/$vertexPath")
    val fShaderStream = getClass.getResourceAsStream(s"/shaders/$fragmentPath")
    // convert stream into string
    val vShaderCode =
      try Source.fromInputStream(vShaderStream).mkString
      finally vShaderStream.close()
    val fShaderCode =
      try Source.fromInputStream(fShaderStream).mkString
      finally fShaderStream.close()

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
    // shader Program
    override val ID = glCreateProgram()
    glAttachShader(ID, vertex)
    glAttachShader(ID, fragment)
    glLinkProgram(ID)
    checkCompileErrors(ID, "PROGRAM")
    // delete the shaders as they're linked into our program now and no longer necessary
    glDeleteShader(vertex)
    glDeleteShader(fragment)

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
  }

}
