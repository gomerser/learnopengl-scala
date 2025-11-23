package learnopengl_4_6

import learnopengl.shader_s.Shader
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600

// stores how much we're seeing of either texture
var mixValue = 0.2f

@main def main(): Unit =

  // glfw: initialize and configure
  // ------------------------------
  if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW")
  GLFWErrorCallback.createPrint(System.err).set()
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
  glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
  glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

  if (System.getProperty("os.name").toLowerCase.contains("mac"))
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE)

  // glfw window creation
  // --------------------
  val window = glfwCreateWindow(SCR_WIDTH, SCR_HEIGHT, "LearnOpenGL", 0, 0)
  if (window == 0L) {
    glfwTerminate()
    throw new RuntimeException("Failed to create GLFW window")
  }
  glfwMakeContextCurrent(window)
  glfwSetFramebufferSizeCallback(window, framebuffer_size_callback)

  // tell GLFW to capture our mouse
  glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
  
  // tell GLFW to capture our mouse
  glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
  
  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // build and compile our shader zprogram
  // ------------------------------------
  val ourShader = Shader(
    "1.getting_started/4.5.texture.vs",
    "1.getting_started/4.5.texture.fs"
  )

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val vertices: Array[Float] = Array(
    // positions          // colors           // texture coords
    0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, // top right
    0.5f, -0.5f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, // bottom right
    -0.5f, -0.5f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, // bottom left
    -0.5f, 0.5f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f // top left
  )
  val indices: Array[Int] = Array(
    0, 1, 3, // first triangle
    1, 2, 3 // second triangle
  )
  val VAO = glGenVertexArrays()
  val VBO = glGenBuffers()
  val EBO = glGenBuffers()

  // bind the Vertex Array Object first, then bind and set vertex buffer(s), and then configure vertex attributes(s).
  glBindVertexArray(VAO)

  glBindBuffer(GL_ARRAY_BUFFER, VBO)
  val vertexBuf = MemoryUtil.memAllocFloat(vertices.length)
  vertexBuf.put(vertices).flip()
  glBufferData(GL_ARRAY_BUFFER, vertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(vertexBuf)

  glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, EBO)
  val indexBuf = MemoryUtil.memAllocInt(indices.length)
  indexBuf.put(indices).flip()
  glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(indexBuf)

  // position attribute
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(0)
  // color attribute
  glVertexAttribPointer(
    1,
    3,
    GL_FLOAT,
    false,
    8 * java.lang.Float.BYTES,
    3 * java.lang.Float.BYTES
  )
  glEnableVertexAttribArray(1)
  // texture coord attribute
  glVertexAttribPointer(
    2,
    2,
    GL_FLOAT,
    false,
    8 * java.lang.Float.BYTES,
    6 * java.lang.Float.BYTES
  )
  glEnableVertexAttribArray(2)

  // load and create a texture
  // -------------------------
  // texture 1
  // ---------
  val texture1: Int = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, texture1)
  // set the texture wrapping parameters
  // note that we set the container wrapping method to GL_CLAMP_TO_EDGE
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
  // set texture filtering parameters
  // set texture filtering to nearest neighbor to clearly see the texels/pixels
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
  // load image, create texture and generate mipmaps
  stbi_set_flip_vertically_on_load(
    true
  ) // tell stb_image.h to flip loaded texture's on the y-axis.
  // The FileSystem::getPath(...) is part of the GitHub repository so we can find files on any IDE/platform; replace it with your own image path.
  usingStack { stack =>
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    val channels = stack.mallocInt(1)

    // Load the image file
    val imgBytes = loadResourceAsTexture("/textures/container.jpg")
    val data = stbi_load_from_memory(imgBytes, w, h, channels, 3)
    if data != null then
      val width = w.get(0)
      val height = h.get(0)

      // Upload texture to OpenGL
      glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_RGB,
        width,
        height,
        0,
        GL_RGB,
        GL_UNSIGNED_BYTE,
        data
      )

      glGenerateMipmap(GL_TEXTURE_2D)
    else println("Failed to load texture")

    // free the image memory
    stbi_image_free(data)
  }
  // texture 2
  // ---------
  val texture2: Int = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, texture2)
  // set the texture wrapping parameters
  // set texture wrapping to GL_REPEAT (default wrapping method)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
  // set texture filtering parameters
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
  // load image, create texture and generate mipmaps
  usingStack { stack =>
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    val channels = stack.mallocInt(1)

    // Load the image file
    val imgBytes = loadResourceAsTexture("/textures/awesomeface.png")
    val data = stbi_load_from_memory(imgBytes, w, h, channels, 3)
    if data != null then
      val width = w.get(0)
      val height = h.get(0)

      // Upload texture to OpenGL
      glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_RGB,
        width,
        height,
        0,
        GL_RGB,
        GL_UNSIGNED_BYTE,
        data
      )

      glGenerateMipmap(GL_TEXTURE_2D)
    else println("Failed to load texture")

    // free the image memory
    stbi_image_free(data)
  }

  // tell opengl for each sampler to which texture unit it belongs to (only has to be done once)
  // -------------------------------------------------------------------------------------------
  ourShader
    .use() // don't forget to activate/use the shader before setting uniforms!
  // either set it manually like so:
  glUniform1i(glGetUniformLocation(ourShader.ID, "texture1"), 0)
  // or set it via the texture class
  ourShader.setInt("texture2", 1)

  // render loop
  // -----------
  while (!glfwWindowShouldClose(window)) {
    // input
    // -----
    processInput(window)

    // render
    // ------
    glClearColor(0.2f, 0.3f, 0.3f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT)

    // bind textures on corresponding texture units
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, texture1)
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(GL_TEXTURE_2D, texture2)

    // set the texture mix value in the shader
    ourShader.setFloat("mixValue", mixValue);

    // render container
    ourShader.use()
    glBindVertexArray(VAO)
    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0)

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  // ------------------------------------------------------------------------
  glDeleteVertexArrays(VAO)
  glDeleteBuffers(VBO)
  glDeleteBuffers(EBO)

  // glfw: terminate, clearing all previously allocated GLFW resources.
  // ------------------------------------------------------------------
  glfwTerminate()

// process all input: query GLFW whether relevant keys are pressed/released this frame and react accordingly
// ---------------------------------------------------------------------------------------------------------
def processInput(window: Long): Unit = {
  if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS)
    glfwSetWindowShouldClose(window, true)

  if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) {
    mixValue += 0.001f; // change this value accordingly (might be too slow or too fast based on system hardware)
    if (mixValue >= 1.0f)
      mixValue = 1.0f;
  }
  if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) {
    mixValue -= 0.001f; // change this value accordingly (might be too slow or too fast based on system hardware)
    if (mixValue <= 0.0f)
      mixValue = 0.0f;
  }
}

// glfw: whenever the window size changed (by OS or user resize) this callback function executes
// ---------------------------------------------------------------------------------------------
def framebuffer_size_callback(window: Long, width: Int, height: Int): Unit = {
  // make sure the viewport matches the new window dimensions; note that width and
  // height will be significantly larger than specified on retina displays.
  glViewport(0, 0, width, height)
}

def usingStack[A](f: MemoryStack => A): A =
  val stack = MemoryStack.stackPush()
  try f(stack)
  finally stack.pop()

def loadResourceAsTexture(name: String): ByteBuffer =
  val stream = this.getClass.getResourceAsStream(name)
  if stream == null then
    throw new RuntimeException("Resource not found: " + name)

  val bytes = stream.readAllBytes()
  val buffer = BufferUtils.createByteBuffer(bytes.length)
  buffer.put(bytes)
  buffer.flip()
  buffer
