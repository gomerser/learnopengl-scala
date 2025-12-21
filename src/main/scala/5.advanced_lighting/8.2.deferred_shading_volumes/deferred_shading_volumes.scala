package learnopengl_5_8_2

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.model.Model
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
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq.ofBoolean
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600

// camera
val camera = Camera(Vector3f(0.0f, 0.0f, 5.0f))
var lastX = SCR_WIDTH / 2.0f
var lastY = SCR_HEIGHT / 2.0f
var firstMouse: Boolean = true

// timing
var deltaTime: Float = 0.0f
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

  // tell GLFW to capture our mouse
  glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

  // fetch framebuffer width and height
  val (fbWidth, fbHeight) = usingStack { stack =>
    val xBuf = stack.mallocInt(1)
    val yBuf = stack.mallocInt(1)
    glfwGetFramebufferSize(window, xBuf, yBuf)
    (xBuf.get(0), yBuf.get(0))
  }

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // tell stb_image.h to flip loaded texture's on the y-axis (before loading model).
  stbi_set_flip_vertically_on_load(true)

  // configure global opengl state
  // -----------------------------
  glEnable(GL_DEPTH_TEST)

  // build and compile shaders
  // ------------------------------------
  val shaderGeometryPass =
    Shader(
      "5.advanced_lighting/8.2.g_buffer.vs",
      "5.advanced_lighting/8.2.g_buffer.fs"
    )
  val shaderLightingPass =
    Shader(
      "5.advanced_lighting/8.2.deferred_shading.vs",
      "5.advanced_lighting/8.2.deferred_shading.fs"
    )
  val shaderLightBox =
    Shader(
      "5.advanced_lighting/8.2.deferred_light_box.vs",
      "5.advanced_lighting/8.2.deferred_light_box.fs"
    )

  // load models
  // -----------
  val backpack = Model("src/main/resources/objects/backpack/backpack.obj")
  val objectPositions = ArrayBuffer[Vector3f]()
  objectPositions += Vector3f(-3.0, -0.5, -3.0)
  objectPositions += Vector3f(0.0, -0.5, -3.0)
  objectPositions += Vector3f(3.0, -0.5, -3.0)
  objectPositions += Vector3f(-3.0, -0.5, 0.0)
  objectPositions += Vector3f(0.0, -0.5, 0.0)
  objectPositions += Vector3f(3.0, -0.5, 0.0)
  objectPositions += Vector3f(-3.0, -0.5, 3.0)
  objectPositions += Vector3f(0.0, -0.5, 3.0)
  objectPositions += Vector3f(3.0, -0.5, 3.0)

  // configure g-buffer framebuffer
  // ------------------------------
  val gBuffer = glGenFramebuffers()
  glBindFramebuffer(GL_FRAMEBUFFER, gBuffer)
  val gPosition = glGenTextures()
  // position color buffer
  glBindTexture(GL_TEXTURE_2D, gPosition)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RGBA16F,
    fbWidth,
    fbHeight,
    0,
    GL_RGBA,
    GL_FLOAT,
    0L
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT0,
    GL_TEXTURE_2D,
    gPosition,
    0
  )
  // normal color buffer
  val gNormal = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, gNormal)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RGBA16F,
    fbWidth,
    fbHeight,
    0,
    GL_RGBA,
    GL_FLOAT,
    0L
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT1,
    GL_TEXTURE_2D,
    gNormal,
    0
  )
  // color + specular color buffer
  val gAlbedoSpec = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, gAlbedoSpec)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RGBA,
    fbWidth,
    fbHeight,
    0,
    GL_RGBA,
    GL_UNSIGNED_BYTE,
    0L
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT2,
    GL_TEXTURE_2D,
    gAlbedoSpec,
    0
  )
  // tell OpenGL which color attachments we'll use (of this framebuffer) for rendering
  usingStack { stack =>
    val buffers = stack.mallocInt(3)
    buffers.put(GL_COLOR_ATTACHMENT0)
    buffers.put(GL_COLOR_ATTACHMENT1)
    buffers.put(GL_COLOR_ATTACHMENT2)
    buffers.flip()
    glDrawBuffers(buffers)
  }
  // create and attach depth buffer (renderbuffer)
  val rboDepth = glGenRenderbuffers()
  glBindRenderbuffer(GL_RENDERBUFFER, rboDepth)
  glRenderbufferStorage(
    GL_RENDERBUFFER,
    GL_DEPTH_COMPONENT,
    fbWidth,
    fbHeight
  )
  glFramebufferRenderbuffer(
    GL_FRAMEBUFFER,
    GL_DEPTH_ATTACHMENT,
    GL_RENDERBUFFER,
    rboDepth
  )
  // finally check if framebuffer is complete
  if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
    throw new RuntimeException("Framebuffer not complete!")
  glBindFramebuffer(GL_FRAMEBUFFER, 0)

  // lighting info
  // -------------
  // positions
  val NR_LIGHTS = 32
  val lightPositions = ArrayBuffer[Vector3f]()
  // colors
  val lightColors = ArrayBuffer[Vector3f]()

  val rand = new Random(13)
  for (i <- 0 until NR_LIGHTS) {
    // calculate slightly random offsets
    val xPos = (((rand.nextInt(100) % 100) / 100.0) * 6.0 - 3.0).toFloat
    val yPos = (((rand.nextInt(100) % 100) / 100.0) * 6.0 - 4.0).toFloat
    val zPos = (((rand.nextInt(100) % 100) / 100.0) * 6.0 - 3.0).toFloat
    lightPositions += Vector3f(xPos, yPos, zPos)
    // also calculate random color
    val rColor =
      (((rand.nextInt(
        100
      ) % 100) / 200.0f) + 0.5).toFloat // between 0.5 and 1.0
    val gColor =
      (((rand.nextInt(
        100
      ) % 100) / 200.0f) + 0.5).toFloat // between 0.5 and 1.0
    val bColor =
      (((rand.nextInt(
        100
      ) % 100) / 200.0f) + 0.5).toFloat // between 0.5 and 1.0
    lightColors += Vector3f(rColor, gColor, bColor)
  }

  // shader configuration
  // --------------------
  shaderLightingPass.use()
  shaderLightingPass.setInt("gPosition", 0)
  shaderLightingPass.setInt("gNormal", 1)
  shaderLightingPass.setInt("gAlbedoSpec", 2)

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
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    // 1. geometry pass: render scene's geometry/color data into gbuffer
    // -----------------------------------------------------------------
    glBindFramebuffer(GL_FRAMEBUFFER, gBuffer)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    val projection = new Matrix4f()
      .perspective(
        Math.toRadians(camera.zoom).toFloat,
        fbWidth.toFloat / fbHeight.toFloat,
        0.1f,
        100.0f
      )
    val view = camera.getViewMatrix
    shaderGeometryPass.use()
    shaderGeometryPass.setMat4("projection", projection)
    shaderGeometryPass.setMat4("view", view)
    for (i <- 0 until objectPositions.length) {
      val model = new Matrix4f()
        .translate(objectPositions(i))
        .scale(0.25f)
      shaderGeometryPass.setMat4("model", model)
      backpack.draw(shaderGeometryPass)
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // 2. lighting pass: calculate lighting by iterating over a screen filled quad pixel-by-pixel using the gbuffer's content.
    // -----------------------------------------------------------------------------------------------------------------------
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    shaderLightingPass.use()
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, gPosition)
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(GL_TEXTURE_2D, gNormal)
    glActiveTexture(GL_TEXTURE2)
    glBindTexture(GL_TEXTURE_2D, gAlbedoSpec)
    // send light relevant uniforms
    for (i <- 0 until lightPositions.size) {
      shaderLightingPass.setVec3(s"lights[$i].Position", lightPositions(i))
      shaderLightingPass.setVec3(s"lights[$i].Color", lightColors(i))
      // update attenuation parameters and calculate radius
      val constant =
        1.0f // note that we don't send this to the shader, we assume it is always 1.0 (in our case)
      val linear = 0.7f
      val quadratic = 1.8f
      shaderLightingPass.setFloat(s"lights[$i].Linear", linear)
      shaderLightingPass.setFloat(s"lights[$i].Quadratic", quadratic)
      // then calculate radius of light volume/sphere
      val maxBrightness =
        math.max(math.max(lightColors(i).x, lightColors(i).y), lightColors(i).z)
      val radius = (-linear + math
        .sqrt(
          linear * linear - 4 * quadratic * (constant - (256.0f / 5.0f) * maxBrightness)
        )
        .toFloat) / (2.0f * quadratic)
      shaderLightingPass.setFloat(s"lights[$i].Radius", radius)
    }
    shaderLightingPass.setVec3("viewPos", camera.position)
    // finally render quad
    renderQuad()

    // 2.5. copy content of geometry's depth buffer to default framebuffer's depth buffer
    // ----------------------------------------------------------------------------------
    glBindFramebuffer(GL_READ_FRAMEBUFFER, gBuffer)
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0) // write to default framebuffer
    // blit to default framebuffer. Note that this may or may not work as the internal formats of both the FBO and default framebuffer have to match.
    // the internal formats are implementation defined. This works on all of my systems, but if it doesn't on yours you'll likely have to write to the
    // depth buffer in another shader stage (or somehow see to match the default framebuffer's internal format with the FBO's internal format).
    glBlitFramebuffer(
      0,
      0,
      fbWidth,
      fbHeight,
      0,
      0,
      fbWidth,
      fbHeight,
      GL_DEPTH_BUFFER_BIT,
      GL_NEAREST
    )
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // 3. render lights on top of scene
    // --------------------------------
    shaderLightBox.use()
    shaderLightBox.setMat4("projection", projection)
    shaderLightBox.setMat4("view", view)
    for (i <- 0 until lightPositions.length) {
      val model = new Matrix4f()
        .translate(lightPositions(i))
        .scale(0.125f)
      shaderLightBox.setMat4("model", model)
      shaderLightBox.setVec3("lightColor", lightColors(i))
      renderCube()
    }

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  glfwTerminate()

// renders a 1x1 quad in NDC with manually calculated tangent vectors
// ------------------------------------------------------------------
var cubeVAO = 0
var cubeVBO = 0
def renderCube() = {
  if (cubeVAO == 0) {

    val vertices = Array(
      // back face
      -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, // bottom-left
      1.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 1.0f, // top-right
      1.0f, -1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, // bottom-right
      1.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 1.0f, // top-right
      -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, // bottom-left
      -1.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, // top-left
      // front face
      -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, // bottom-left
      1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, // bottom-right
      1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, // top-right
      1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, // top-right
      -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, // top-left
      -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, // bottom-left
      // left face
      -1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, // top-right
      -1.0f, 1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 1.0f, 1.0f, // top-left
      -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, // bottom-left
      -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, // bottom-left
      -1.0f, -1.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, // bottom-right
      -1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, // top-right
      // right face
      1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, // top-left
      1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, // bottom-right
      1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, // top-right
      1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, // bottom-right
      1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, // top-left
      1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, // bottom-left
      // bottom face
      -1.0f, -1.0f, -1.0f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, // top-right
      1.0f, -1.0f, -1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 1.0f, // top-left
      1.0f, -1.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f, // bottom-left
      1.0f, -1.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f, // bottom-left
      -1.0f, -1.0f, 1.0f, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f, // bottom-right
      -1.0f, -1.0f, -1.0f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, // top-right
      // top face
      -1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, // top-left
      1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, // bottom-right
      1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, // top-right
      1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, // bottom-right
      -1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, // top-left
      -1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f // bottom-left
    )
    cubeVAO = glGenVertexArrays()
    cubeVBO = glGenBuffers()
    // fill buffer
    glBindVertexArray(cubeVAO)
    glBindBuffer(GL_ARRAY_BUFFER, cubeVBO)
    val quadVertexBuf = MemoryUtil.memAllocFloat(vertices.length)
    quadVertexBuf.put(vertices).flip()
    glBufferData(GL_ARRAY_BUFFER, quadVertexBuf, GL_STATIC_DRAW)
    MemoryUtil.memFree(quadVertexBuf)
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * java.lang.Float.BYTES, 0)
    glEnableVertexAttribArray(1)
    glVertexAttribPointer(
      1,
      3,
      GL_FLOAT,
      false,
      8 * java.lang.Float.BYTES,
      3 * java.lang.Float.BYTES
    )
    glEnableVertexAttribArray(2)
    glVertexAttribPointer(
      2,
      2,
      GL_FLOAT,
      false,
      8 * java.lang.Float.BYTES,
      6 * java.lang.Float.BYTES
    )
  }
  // render Cube
  glBindVertexArray(cubeVAO)
  glDrawArrays(GL_TRIANGLES, 0, 36)
  glBindVertexArray(0)
}

// renderQuad() renders a 1x1 XY quad in NDC
// -----------------------------------------
var quadVAO = 0
var quadVBO = 0
def renderQuad() = {
  if (quadVAO == 0) {

    val quadVertices = Array(
      // positions        // texture Coords
      -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f,
      0.0f, 1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f
    )
    // setup plane VAO
    quadVAO = glGenVertexArrays()
    quadVBO = glGenBuffers()
    glBindVertexArray(quadVAO)
    glBindBuffer(GL_ARRAY_BUFFER, quadVBO)
    val quadVertexBuf = MemoryUtil.memAllocFloat(quadVertices.length)
    quadVertexBuf.put(quadVertices).flip()
    glBufferData(GL_ARRAY_BUFFER, quadVertexBuf, GL_STATIC_DRAW)
    MemoryUtil.memFree(quadVertexBuf)
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * java.lang.Float.BYTES, 0)
    glEnableVertexAttribArray(1)
    glVertexAttribPointer(
      1,
      2,
      GL_FLOAT,
      false,
      5 * java.lang.Float.BYTES,
      3 * java.lang.Float.BYTES
    )
  }
  glBindVertexArray(quadVAO)
  glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
  glBindVertexArray(0)
}
// process all input: query GLFW whether relevant keys are pressed/released this frame and react accordingly
// ---------------------------------------------------------------------------------------------------------
def processInput(window: Long): Unit = {
  if glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS then
    glfwSetWindowShouldClose(window, true)

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
