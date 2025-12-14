package learnopengl_5_3_3

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.shader.Shader
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
import org.lwjgl.opengl.GL21.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL32.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer
import scala.collection.mutable.ListBuffer

// Properties
val screenWidth = 800
val screenHeight = 600

// camera
val camera = Camera(Vector3f(0.0f, 0.0f, 3.0f))
val keys = Array.fill(1024)(false)
var lastX = 400
var lastY = 300
var firstMouse: Boolean = true

// timing
var deltaTime: Float = 0.0f
var lastFrame: Float = 0.0f

// The MAIN function, from here we start our application and run our Game loop
@main def main(): Unit =

  // glfw window creation
  // --------------------
  if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW")
  GLFWErrorCallback.createPrint(System.err).set()
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
  glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
  glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

  if (System.getProperty("os.name").toLowerCase.contains("mac"))
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE)

  // glfw window creation
  // --------------------
  val window = glfwCreateWindow(screenWidth, screenHeight, "LearnOpenGL", 0, 0)
  if (window == 0L) {
    glfwTerminate()
    throw new RuntimeException("Failed to create GLFW window")
  }
  glfwMakeContextCurrent(window)

  // Set the required callback functions
  glfwSetKeyCallback(window, key_callback)
  glfwSetCursorPosCallback(window, mouse_callback)
  glfwSetScrollCallback(window, scroll_callback)

  // Options
  glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

  // fetch framebuffer width and height
  val (fbWidth, fbHeight) = usingStack { stack =>
    val xBuf = stack.mallocInt(1)
    val yBuf = stack.mallocInt(1)
    glfwGetFramebufferSize(window, xBuf, yBuf)
    (xBuf.get(0), yBuf.get(0))
  }

  // Initialize GLEW to setup the OpenGL Function pointers
  val glewExperimental = true
  GL.createCapabilities()

  // Define the viewport dimensions
  glViewport(0, 0, fbWidth, fbHeight)

  // Setup some OpenGL options
  glEnable(GL_DEPTH_TEST)
  // glDepthFunc(GL_ALWAYS) // Set to always pass the depth test (same effect as glDisable(GL_DEPTH_TEST))

  // Setup and compile our shaders
  val shader =
    Shader(
      "4.advanced_opengl/1.1.depth_testing.vs",
      "4.advanced_opengl/1.1.depth_testing.fs"
    )

  // Set the object data (buffers, vertex attributes)
  val cubeVertices: Array[Float] = Array(
    // Positions          // Texture Coords
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
  val planeVertices = Array[Float](
    // Positions            // Texture Coords (note we set these higher than 1 that together with GL_REPEAT as texture wrapping mode will cause the floor texture to repeat)
    5.0f, -0.5f, 5.0f, 2.0f, 0.0f, -5.0f, -0.5f, 5.0f, 0.0f, 0.0f, -5.0f, -0.5f,
    -5.0f, 0.0f, 2.0f, 5.0f, -0.5f, 5.0f, 2.0f, 0.0f, -5.0f, -0.5f, -5.0f, 0.0f,
    2.0f, 5.0f, -0.5f, -5.0f, 2.0f, 2.0f
  )
  // Setup cube VAO
  val cubeVAO = glGenVertexArrays()
  val cubeVBO = glGenBuffers()
  glBindVertexArray(cubeVAO)
  glBindBuffer(GL_ARRAY_BUFFER, cubeVBO)
  val cubeVertexBuf = MemoryUtil.memAllocFloat(cubeVertices.length)
  cubeVertexBuf.put(cubeVertices).flip()
  glBufferData(GL_ARRAY_BUFFER, cubeVertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(cubeVertexBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(1)
  glVertexAttribPointer(
    1,
    2,
    GL_FLOAT,
    false,
    5 * java.lang.Float.BYTES,
    3 * java.lang.Float.BYTES
  )
  glBindVertexArray(0)
  // Setup plane VAO
  val planeVAO = glGenVertexArrays()
  val planeVBO = glGenBuffers()
  glBindVertexArray(planeVAO)
  glBindBuffer(GL_ARRAY_BUFFER, planeVBO)
  val planeVertexBuf = MemoryUtil.memAllocFloat(planeVertices.length)
  planeVertexBuf.put(planeVertices).flip()
  glBufferData(GL_ARRAY_BUFFER, planeVertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(planeVertexBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(1)
  glVertexAttribPointer(
    1,
    2,
    GL_FLOAT,
    false,
    5 * java.lang.Float.BYTES,
    3 * java.lang.Float.BYTES
  )
  glBindVertexArray(0)

  // Load textures
  val cubeTexture = loadTexture("/textures/marble.jpg")
  val floorTexture = loadTexture("/textures/metal.png")

  // Game loop
  while (!glfwWindowShouldClose(window)) {
    // Set frame time
    val currentFrame = glfwGetTime().toFloat
    deltaTime = currentFrame - lastFrame
    lastFrame = currentFrame

    // Check and call events
    glfwPollEvents()
    Do_Movement()

    // Clear the colorbuffer
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    // Draw objects
    shader.use()
    val view = camera.getViewMatrix
    val projection = new Matrix4f()
      .perspective(
        Math.toRadians(camera.zoom).toFloat,
        screenWidth.toFloat / screenHeight.toFloat,
        0.1f,
        100.0f
      )
    usingStack { stack =>
      val fb = stack.mallocFloat(16)

      glUniformMatrix4fv(
        glGetUniformLocation(shader.ID, "view"),
        false,
        view.get(fb)
      )

      glUniformMatrix4fv(
        glGetUniformLocation(shader.ID, "projection"),
        false,
        projection.get(fb)
      )
    } // Cubes
    glBindVertexArray(cubeVAO)
    glBindTexture(GL_TEXTURE_2D, cubeTexture)
    usingStack { stack =>
      val model = new Matrix4f()
        .translate(-1.0f, 0.0f, -1.0f)

      val fb = stack.mallocFloat(16)
      glUniformMatrix4fv(
        glGetUniformLocation(shader.ID, "model"),
        false,
        model.get(fb)
      )
      glDrawArrays(GL_TRIANGLES, 0, 36)
    }
    usingStack { stack =>
      val model = new Matrix4f()
        .translate(2.0f, 0.0f, 0.0f)

      val fb = stack.mallocFloat(16)
      glUniformMatrix4fv(
        glGetUniformLocation(shader.ID, "model"),
        false,
        model.get(fb)
      )
      glDrawArrays(GL_TRIANGLES, 0, 36)
    }
    // Floor
    glBindVertexArray(planeVAO)
    glBindTexture(GL_TEXTURE_2D, floorTexture)

    usingStack { stack =>
      val model = new Matrix4f() // identity
      val fb = stack.mallocFloat(16)

      glUniformMatrix4fv(
        glGetUniformLocation(shader.ID, "model"),
        false,
        model.get(fb)
      )
      glDrawArrays(GL_TRIANGLES, 0, 6)
    }
    glBindVertexArray(0)

    // Swap the buffers
    glfwSwapBuffers(window)
  }

  glfwTerminate()

// This function loads a texture from file. Note: texture loading functions like these are usually
// managed by a 'Resource Manager' that manages all resources (like textures, models, audio).
// For learning purposes we'll just define it as a utility function.
def loadTexture(path: String): Int =
  val textureID = glGenTextures()

  usingStack { stack =>
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    val nrComponents = stack.mallocInt(1)

    val imgBytes = loadResourceAsTexture(path)
    val data = stbi_load_from_memory(imgBytes, w, h, nrComponents, 0)
    if (data == null)
      throw new RuntimeException("Failed to load texture: " + path)

    val format = nrComponents.get() match {
      case 1 => GL_RED
      case 4 => GL_RGBA
      case 3 => GL_RGB
    }

    glBindTexture(GL_TEXTURE_2D, textureID)
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      format,
      w.get(),
      h.get(),
      0,
      format,
      GL_UNSIGNED_BYTE,
      data
    )
    glGenerateMipmap(GL_TEXTURE_2D)

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
    glTexParameteri(
      GL_TEXTURE_2D,
      GL_TEXTURE_MIN_FILTER,
      GL_LINEAR_MIPMAP_LINEAR
    )
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

    stbi_image_free(data)
  }

  textureID

// Moves/alters the camera positions based on user input
def Do_Movement(): Unit =
  // Camera controls
  if (keys(GLFW_KEY_W))
    camera.processKeyboard(FORWARD, deltaTime)
  if (keys(GLFW_KEY_S))
    camera.processKeyboard(BACKWARD, deltaTime)
  if (keys(GLFW_KEY_A))
    camera.processKeyboard(LEFT, deltaTime)
  if (keys(GLFW_KEY_D))
    camera.processKeyboard(RIGHT, deltaTime)

// Is called whenever a key is pressed/released via GLFW
def key_callback(
    window: Long,
    key: Int,
    scancode: Int,
    action: Int,
    mode: Int
): Unit =
  if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS)
    glfwSetWindowShouldClose(window, true)

  if (action == GLFW_PRESS)
    keys(key) = true
  else if (action == GLFW_RELEASE)
    keys(key) = false

def mouse_callback(window: Long, xposIn: Double, yposIn: Double): Unit =
  val xpos = xposIn.toInt
  val ypos = yposIn.toInt

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

  camera.processMouseMovement(xoffset.toFloat, yoffset.toFloat)

def scroll_callback(window: Long, xoffset: Double, yoffset: Double): Unit =
  camera.processMouseScroll(yoffset.toFloat)

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
