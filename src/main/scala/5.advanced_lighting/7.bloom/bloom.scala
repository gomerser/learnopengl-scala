package learnopengl_5_7

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
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq.ofBoolean
import scala.collection.mutable.ArrayBuffer

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600
var bloom = true
var bloomKeyPressed = false
var exposure = 1.0f

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

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // configure global opengl state
  // -----------------------------
  glEnable(GL_DEPTH_TEST)

  // build and compile shaders
  // ------------------------------------
  val shader =
    Shader("5.advanced_lighting/7.bloom.vs", "5.advanced_lighting/7.bloom.fs")
  val shaderLight =
    Shader(
      "5.advanced_lighting/7.bloom.vs",
      "5.advanced_lighting/7.light_box.fs"
    )
  val shaderBlur =
    Shader("5.advanced_lighting/7.blur.vs", "5.advanced_lighting/7.blur.fs")
  val shaderBloomFinal =
    Shader(
      "5.advanced_lighting/7.bloom_final.vs",
      "5.advanced_lighting/7.bloom_final.fs"
    )

  // load textures
  // -------------
  val woodTexture = loadTexture(
    "/textures/wood.png",
    true
  ) // note that we're loading the texture as an SRGB texture
  val containerTexture = loadTexture(
    "/textures/container2.png",
    true
  ) // note that we're loading the texture as an SRGB texture

  // configure (floating point) framebuffers
  // ---------------------------------------
  val hdrFBO = glGenFramebuffers()
  glBindFramebuffer(GL_FRAMEBUFFER, hdrFBO)
  // create 2 floating point color buffers (1 for normal rendering, other for brightness threshold values)
  val colorBuffers = MemoryUtil.memAllocInt(2)
  glGenTextures(colorBuffers)
  for (i <- 0 until 2) {
    glBindTexture(GL_TEXTURE_2D, colorBuffers.get(i))
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      GL_RGBA16F,
      SCR_WIDTH,
      SCR_HEIGHT,
      0,
      GL_RGBA,
      GL_FLOAT,
      0L
    )
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    glTexParameteri(
      GL_TEXTURE_2D,
      GL_TEXTURE_WRAP_S,
      GL_CLAMP_TO_EDGE
    ) // we clamp to the edge as the blur filter would otherwise sample repeated texture values!
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    // attach texture to framebuffer
    glFramebufferTexture2D(
      GL_FRAMEBUFFER,
      GL_COLOR_ATTACHMENT0 + i,
      GL_TEXTURE_2D,
      colorBuffers.get(i),
      0
    )
  }
  // create and attach depth buffer (renderbuffer)
  val rboDepth = glGenRenderbuffers()
  glBindRenderbuffer(GL_RENDERBUFFER, rboDepth)
  glRenderbufferStorage(
    GL_RENDERBUFFER,
    GL_DEPTH_COMPONENT,
    SCR_WIDTH,
    SCR_HEIGHT
  )
  glFramebufferRenderbuffer(
    GL_FRAMEBUFFER,
    GL_DEPTH_ATTACHMENT,
    GL_RENDERBUFFER,
    rboDepth
  )
  // tell OpenGL which color attachments we'll use (of this framebuffer) for rendering
  val attachments = Array(GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1)
  glDrawBuffers(attachments)
  // finally check if framebuffer is complete
  if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
    println("Framebuffer not complete!")
  glBindFramebuffer(GL_FRAMEBUFFER, 0)

  // ping-pong-framebuffer for blurring
  val pingpongFBO = MemoryUtil.memAllocInt(2)
  val pingpongColorbuffers = MemoryUtil.memAllocInt(2)

  glGenFramebuffers(pingpongFBO)
  glGenTextures(pingpongColorbuffers)
  for (i <- 0 until 2) {
    glBindFramebuffer(GL_FRAMEBUFFER, pingpongFBO.get(i))
    glBindTexture(GL_TEXTURE_2D, pingpongColorbuffers.get(i))
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      GL_RGBA16F,
      SCR_WIDTH,
      SCR_HEIGHT,
      0,
      GL_RGBA,
      GL_FLOAT,
      0L
    )
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    glTexParameteri(
      GL_TEXTURE_2D,
      GL_TEXTURE_WRAP_S,
      GL_CLAMP_TO_EDGE
    ) // we clamp to the edge as the blur filter would otherwise sample repeated texture values!
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    glFramebufferTexture2D(
      GL_FRAMEBUFFER,
      GL_COLOR_ATTACHMENT0,
      GL_TEXTURE_2D,
      pingpongColorbuffers.get(i),
      0
    )
    // also check if framebuffers are complete (no need for depth buffer)
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
      println("Framebuffer not complete!")
  }
  // lighting info
  // -------------
  // positions
  val lightPositions = ArrayBuffer[Vector3f]()
  lightPositions += Vector3f(0.0f, 0.5f, 1.5f)
  lightPositions += Vector3f(-4.0f, 0.5f, -3.0f)
  lightPositions += Vector3f(3.0f, 0.5f, 1.0f)
  lightPositions += Vector3f(-0.8f, 2.4f, -1.0f)
  // colors
  val lightColors = ArrayBuffer[Vector3f]()
  lightColors += Vector3f(5.0f, 5.0f, 5.0f)
  lightColors += Vector3f(10.0f, 0.0f, 0.0f)
  lightColors += Vector3f(0.0f, 0.0f, 15.0f)
  lightColors += Vector3f(0.0f, 5.0f, 0.0f)

  // shader configuration
  // --------------------
  shader.use()
  shader.setInt("diffuseTexture", 0)
  shaderBlur.use()
  shaderBlur.setInt("image", 0)
  shaderBloomFinal.use()
  shaderBloomFinal.setInt("scene", 0)
  shaderBloomFinal.setInt("bloomBlur", 1)

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

    // 1. render scene into floating point framebuffer
    // -----------------------------------------------
    glBindFramebuffer(GL_FRAMEBUFFER, hdrFBO)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    val projection = new Matrix4f()
      .perspective(
        Math.toRadians(camera.zoom).toFloat,
        SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
        0.1f,
        100.0f
      )
    val view = camera.getViewMatrix
    shader.use()
    shader.setMat4("projection", projection)
    shader.setMat4("view", view)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, woodTexture)
    // set lighting uniforms
    for (i <- 0 until lightPositions.size) {
      shader.setVec3(s"lights[$i].Position", lightPositions(i))
      shader.setVec3(s"lights[$i].Color", lightColors(i))
    }
    shader.setVec3("viewPos", camera.position)
    // create one large cube that acts as the floor
    var model = new Matrix4f()
      .translate(Vector3f(0.0f, -1.0f, 0.0))
      .scale(Vector3f(12.5f, 0.5f, 15.5f))
    shader.setMat4("model", model)
    renderCube()
    // then create multiple cubes as the scenery
    model = new Matrix4f()
      .translate(Vector3f(0.0f, 1.5f, 0.0))
      .scale(0.5f)
    shader.setMat4("model", model)
    renderCube()

    model = new Matrix4f()
      .translate(Vector3f(2.0f, 0.0f, 1.0))
      .scale(0.5f)
    shader.setMat4("model", model)
    renderCube()

    model = new Matrix4f()
      .translate(Vector3f(-1.0f, -1.0f, 2.0))
      .rotate(
        Math.toRadians(60.0f).toFloat,
        Vector3f(1.0f, 0.0f, 1.0f).normalize
      )
    shader.setMat4("model", model)
    renderCube()

    model = new Matrix4f()
      .translate(Vector3f(0.0f, 2.7f, 4.0))
      .rotate(
        Math.toRadians(23.0f).toFloat,
        Vector3f(1.0f, 0.0f, 1.0f).normalize
      )
      .scale(1.25f)
    shader.setMat4("model", model)
    renderCube()

    model = new Matrix4f()
      .translate(Vector3f(-2.0f, 1.0f, -3.0))
      .rotate(
        Math.toRadians(124.0f).toFloat,
        Vector3f(1.0f, 0.0f, 1.0f).normalize
      )
    shader.setMat4("model", model)
    renderCube()

    model = new Matrix4f()
      .translate(Vector3f(-3.0f, 0.0f, 0.0))
      .scale(0.5f)
    shader.setMat4("model", model)
    renderCube()

    // finally show all the light sources as bright cubes
    shaderLight.use()
    shaderLight.setMat4("projection", projection)
    shaderLight.setMat4("view", view)

    for (i <- 0 until lightPositions.size) {
      model = new Matrix4f()
        .translate(lightPositions(i))
        .scale(0.25f)
      shaderLight.setMat4("model", model)
      shaderLight.setVec3("lightColor", lightColors(i))
      renderCube()
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // 2. blur bright fragments with two-pass Gaussian Blur
    // --------------------------------------------------
    var horizontal = true
    var first_iteration = true
    val amount = 10
    shaderBlur.use()
    for (i <- 0 until amount) {
      glBindFramebuffer(
        GL_FRAMEBUFFER,
        pingpongFBO.get(if (horizontal) 1 else 0)
      )
      shaderBlur.setBool("horizontal", horizontal)
      glBindTexture(
        GL_TEXTURE_2D,
        if (first_iteration) colorBuffers.get(1)
        else pingpongColorbuffers.get(if (horizontal) 0 else 1)
      ) // bind texture of other framebuffer (or scene if first iteration)
      renderQuad()
      horizontal = !horizontal
      if (first_iteration)
        first_iteration = false
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // 3. now render floating point color buffer to 2D quad and tonemap HDR colors to default framebuffer's (clamped) color range
    // --------------------------------------------------------------------------------------------------------------------------
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    shaderBloomFinal.use()
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, colorBuffers.get(0))
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(
      GL_TEXTURE_2D,
      pingpongColorbuffers.get(if (horizontal) 0 else 1)
    )
    shaderBloomFinal.setBool("bloom", bloom)
    shaderBloomFinal.setFloat("exposure", exposure)
    renderQuad()

    println(s"bloom: ${if (bloom) "on" else "off"} | exposure: $exposure")

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

  if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS && !bloomKeyPressed) {
    bloom = !bloom;
    bloomKeyPressed = true;
  }
  if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_RELEASE) {
    bloomKeyPressed = false;
  }

  if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) {
    if (exposure > 0.0f)
      exposure -= 0.001f
    else
      exposure = 0.0f
  } else if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) {
    exposure += 0.001f
  }
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

// utility function for loading a 2D texture from file
// ---------------------------------------------------
def loadTexture(path: String, gammaCorrection: Boolean): Int = {
  val textureID = glGenTextures()

  usingStack { stack =>
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    val nrComponents = stack.mallocInt(1)

    val imgBytes = loadResourceAsTexture(path)
    val data = stbi_load_from_memory(imgBytes, w, h, nrComponents, 0)
    if (data == null)
      throw new RuntimeException("Failed to load texture: " + path)

    val (internalFormat, dataFormat) = nrComponents.get() match {
      case 1 => (GL_RED, GL_RED)
      case 3 => (if (gammaCorrection) GL_SRGB else GL_RGB, GL_RGB)
      case 4 => (if (gammaCorrection) GL_SRGB_ALPHA else GL_RGBA, GL_RGBA)
    }

    glBindTexture(GL_TEXTURE_2D, textureID)
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      internalFormat,
      w.get(),
      h.get(),
      0,
      dataFormat,
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
