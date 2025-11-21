package learnopengl_7_4

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.shader_m.Shader
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

// camera
val camera = Camera(Vector3f(0.0f, 0.0f, 3.0f))
var lastX = SCR_WIDTH / 2.0f
var lastY = SCR_HEIGHT / 2.0f
var firstMouse: Boolean = true

// timing
var deltaTime: Float = 0.0f // time between current frame and last frame
var lastFrame: Float = 0.0f

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
  glfwSetCursorPosCallback(window, mouse_callback)
  glfwSetScrollCallback(window, scroll_callback)

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // configure global opengl state
  // -----------------------------
  glEnable(GL_DEPTH_TEST)

  // build and compile our shader zprogram
  // ------------------------------------
  val ourShader =
    Shader("1.getting_started/7.3.camera.vs", "1.getting_started/7.3.camera.fs")

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val vertices: Array[Float] = Array(
    -0.5f, -0.5f, -0.5f, 0.0f, 0.0f, 0.5f, -0.5f, -0.5f, 1.0f, 0.0f, 0.5f, 0.5f,
    -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -0.5f, 1.0f, 1.0f, -0.5f, 0.5f, -0.5f, 0.0f,
    1.0f, -0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 0.5f,
    -0.5f, 0.5f, 1.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 1.0f, 0.5f, 0.5f, 0.5f,
    1.0f, 1.0f, -0.5f, 0.5f, 0.5f, 0.0f, 1.0f, -0.5f, -0.5f, 0.5f, 0.0f, 0.0f,
    -0.5f, 0.5f, 0.5f, 1.0f, 0.0f, -0.5f, 0.5f, -0.5f, 1.0f, 1.0f, -0.5f, -0.5f,
    -0.5f, 0.0f, 1.0f, -0.5f, -0.5f, -0.5f, 0.0f, 1.0f, -0.5f, -0.5f, 0.5f,
    0.0f, 0.0f, -0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 0.0f,
    0.5f, 0.5f, -0.5f, 1.0f, 1.0f, 0.5f, -0.5f, -0.5f, 0.0f, 1.0f, 0.5f, -0.5f,
    -0.5f, 0.0f, 1.0f, 0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f,
    0.0f, -0.5f, -0.5f, -0.5f, 0.0f, 1.0f, 0.5f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f,
    -0.5f, 0.5f, 1.0f, 0.0f, 0.5f, -0.5f, 0.5f, 1.0f, 0.0f, -0.5f, -0.5f, 0.5f,
    0.0f, 0.0f, -0.5f, -0.5f, -0.5f, 0.0f, 1.0f, -0.5f, 0.5f, -0.5f, 0.0f, 1.0f,
    0.5f, 0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.5f, 0.5f,
    0.5f, 1.0f, 0.0f, -0.5f, 0.5f, 0.5f, 0.0f, 0.0f, -0.5f, 0.5f, -0.5f, 0.0f,
    1.0f
  )

  // world space positions of our cubes
  val cubePositions = Array[Vector3f](
    Vector3f(0.0f, 0.0f, 0.0f),
    Vector3f(2.0f, 5.0f, -15.0f),
    Vector3f(-1.5f, -2.2f, -2.5f),
    Vector3f(-3.8f, -2.0f, -12.3f),
    Vector3f(2.4f, -0.4f, -3.5f),
    Vector3f(-1.7f, 3.0f, -7.5f),
    Vector3f(1.3f, -2.0f, -2.5f),
    Vector3f(1.5f, 2.0f, -2.5f),
    Vector3f(1.5f, 0.2f, -1.5f),
    Vector3f(-1.3f, 1.0f, -1.5f)
  )

  val VAO = glGenVertexArrays()
  val VBO = glGenBuffers()

  // bind the Vertex Array Object first, then bind and set vertex buffer(s), and then configure vertex attributes(s).
  glBindVertexArray(VAO)

  glBindBuffer(GL_ARRAY_BUFFER, VBO)
  val vertexBuf = MemoryUtil.memAllocFloat(vertices.length)
  vertexBuf.put(vertices).flip()
  glBufferData(GL_ARRAY_BUFFER, vertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(vertexBuf)

  // position attribute
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(0)
  // color attribute
  glVertexAttribPointer(
    1,
    2,
    GL_FLOAT,
    false,
    5 * java.lang.Float.BYTES,
    3 * java.lang.Float.BYTES
  )
  glEnableVertexAttribArray(1)

  // load and create a texture
  // -------------------------
  // texture 1
  // ---------
  val texture1: Int = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, texture1)
  // set the texture wrapping parameters
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
  // set texture filtering parameters
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
  ourShader.use()
  ourShader.setInt("texture1", 0)
  ourShader.setInt("texture2", 1)

  // render loop
  // -----------
  while (!glfwWindowShouldClose(window)) {

    // per-frame time logic
    // --------------------
    val currentFrame = glfwGetTime().toFloat
    deltaTime = currentFrame - lastFrame
    lastFrame = currentFrame

    // input
    // -----
    processInput(window)

    // render
    // ------
    glClearColor(0.2f, 0.3f, 0.3f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    // bind textures on corresponding texture units
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, texture1)
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(GL_TEXTURE_2D, texture2)

    // activate shader
    ourShader.use()

    // pass projection matrix to shader (note: can update every frame if fov changes)
    val projection = new Matrix4f()
      .perspective(
        Math.toRadians(camera.zoom).toFloat,
        SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
        0.1f,
        100.0f
      )
    ourShader.setMat4("projection", projection)

    // camera/view transformation
    val view = camera.getViewMatrix
    ourShader.setMat4("view", view)

    // render boxes
    glBindVertexArray(VAO)
    for i <- 0 until 10 do {
      // calculate the model matrix for each object and pass it to shader before drawing
      val model =
        Matrix4f() // make sure to initialize matrix to identity matrix first
          .identity()
          .translate(cubePositions(i))
          .rotate(
            Math.toRadians(20.0f * i).toFloat,
            1.0f,
            0.3f,
            0.5f
          )
      ourShader.setMat4("model", model);

      glDrawArrays(GL_TRIANGLES, 0, 36);
    }

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  // ------------------------------------------------------------------------
  glDeleteVertexArrays(VAO)
  glDeleteBuffers(VBO)

  // glfw: terminate, clearing all previously allocated GLFW resources.
  // ------------------------------------------------------------------
  glfwTerminate()

// process all input: query GLFW whether relevant keys are pressed/released this frame and react accordingly
// ---------------------------------------------------------------------------------------------------------
def processInput(window: Long): Unit = {
  if glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS then
    glfwSetWindowShouldClose(window, true)

  val cameraSpeed = (2.5f * deltaTime)
  if glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS then
    camera.processKeyboard(FORWARD, deltaTime)
  if glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS then
    camera.processKeyboard(BACKWARD, deltaTime)
  if glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS then
    camera.processKeyboard(LEFT, deltaTime)
  if glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS then
    camera.processKeyboard(RIGHT, deltaTime)
}

// glfw: whenever the window size changed (by OS or user resize) this callback function executes
// ---------------------------------------------------------------------------------------------
def framebuffer_size_callback(window: Long, width: Int, height: Int): Unit = {
  // make sure the viewport matches the new window dimensions; note that width and
  // height will be significantly larger than specified on retina displays.
  glViewport(0, 0, width, height)
}

// glfw: whenever the mouse moves, this callback is called
// -------------------------------------------------------
def mouse_callback(window: Long, xposIn: Double, yposIn: Double): Unit = {
  val xpos = xposIn.toFloat
  val ypos = yposIn.toFloat

  if (firstMouse) {
    lastX = xpos
    lastY = ypos
    firstMouse = false
  }

  var xoffset = xpos - lastX
  var yoffset =
    lastY - ypos // reversed since y-coordinates go from bottom to top
  lastX = xpos
  lastY = ypos

  camera.processMouseMovement(xoffset, yoffset)
}

// glfw: whenever the mouse scroll wheel scrolls, this callback is called
// ----------------------------------------------------------------------
def scroll_callback(window: Long, xoffset: Double, yoffset: Double): Unit = {
  camera.processMouseScroll(yoffset.toFloat)
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
