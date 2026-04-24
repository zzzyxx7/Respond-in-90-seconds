const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');

const app = express();
const port = 3000;

// 静态文件服务
app.use(express.static(path.join(__dirname, '../frontend')));
app.use('/img', express.static(path.join(__dirname, '../frontend/img')));

// 解析JSON请求体
app.use(express.json());

// CORS中间件
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }
  next();
});

// 配置文件上传
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    const uploadPath = path.join(__dirname, 'uploads');
    if (!fs.existsSync(uploadPath)) {
      fs.mkdirSync(uploadPath, { recursive: true });
    }
    cb(null, uploadPath);
  },
  filename: function (req, file, cb) {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({ storage: storage });

// 健康检查接口
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', message: 'Server is running' });
});

// 上传模板文件
app.post('/api/upload/template', upload.single('template'), (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No template file uploaded' });
    }

    res.json({
      success: true,
      message: 'Template uploaded successfully',
      data: {
        filename: req.file.originalname,
        path: req.file.path,
        size: req.file.size
      }
    });
  } catch (error) {
    res.status(500).json({ error: 'Failed to upload template' });
  }
});

// 上传文档文件
app.post('/api/upload/documents', upload.array('documents', 10), (req, res) => {
  try {
    if (!req.files || req.files.length === 0) {
      return res.status(400).json({ error: 'No document files uploaded' });
    }

    const files = req.files.map(file => ({
      filename: file.originalname,
      path: file.path,
      size: file.size
    }));

    res.json({
      success: true,
      message: 'Documents uploaded successfully',
      data: files
    });
  } catch (error) {
    res.status(500).json({ error: 'Failed to upload documents' });
  }
});

// 开始处理
app.post('/api/process', (req, res) => {
  try {
    const { template, files, requirements } = req.body;

    if (!template) {
      return res.status(400).json({ error: 'Template is required' });
    }

    if (!files || files.length === 0) {
      return res.status(400).json({ error: 'At least one document is required' });
    }

    if (!requirements) {
      return res.status(400).json({ error: 'Requirements are required' });
    }

    // 模拟处理过程
    setTimeout(() => {
      res.json({
        success: true,
        message: 'Processing completed successfully',
        data: {
          processId: Date.now().toString(),
          steps: [
            '已接收任务',
            '解析模板结构',
            '读取文件内容',
            '提取关键数据',
            '执行数据匹配',
            '验证匹配结果',
            '生成中间文件',
            '优化文档结构',
            '生成最终结果文档'
          ],
          resultFile: '解析完成_结果文件.docx',
          processingTime: '2.5 seconds'
        }
      });
    }, 2000);

  } catch (error) {
    res.status(500).json({ error: 'Failed to process request' });
  }
});

// 上传文档文件（新接口）
app.post('/api/documents/upload', upload.array('files', 10), (req, res) => {
  try {
    if (!req.files || req.files.length === 0) {
      return res.status(400).json({ error: 'No document files uploaded' });
    }

    const files = req.files.map(file => ({
      filename: file.originalname,
      path: file.path,
      size: file.size
    }));

    res.json({
      success: true,
      message: 'Documents uploaded successfully',
      data: {
        documentSetId: Date.now().toString(),
        files: files
      }
    });
  } catch (error) {
    res.status(500).json({ error: 'Failed to upload documents' });
  }
});

// 获取文档集信息
app.get('/api/documents/sets/public/:documentSetId', (req, res) => {
  try {
    const { documentSetId } = req.params;

    res.json({
      success: true,
      message: 'Document set retrieved successfully',
      data: {
        documentSetId: documentSetId,
        files: [
          {
            id: '1',
            filename: 'sample.docx',
            size: 102400,
            type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
          }
        ]
      }
    });
  } catch (error) {
    res.status(500).json({ error: 'Failed to retrieve document set' });
  }
});

// 追加文档到文档集
app.post('/api/documents/sets/public/:documentSetId/append', upload.array('files', 10), (req, res) => {
  try {
    const { documentSetId } = req.params;

    if (!req.files || req.files.length === 0) {
      return res.status(400).json({ error: 'No document files uploaded' });
    }

    const files = req.files.map(file => ({
      filename: file.originalname,
      path: file.path,
      size: file.size
    }));

    res.json({
      success: true,
      message: 'Documents appended successfully',
      data: {
        documentSetId: documentSetId,
        files: files
      }
    });
  } catch (error) {
    res.status(500).json({ error: 'Failed to append documents' });
  }
});

// 启动服务器
app.listen(port, () => {
  console.log(`Server running at http://localhost:${port}`);
  console.log(`Frontend available at http://localhost:${port}/combined_final.html`);
});